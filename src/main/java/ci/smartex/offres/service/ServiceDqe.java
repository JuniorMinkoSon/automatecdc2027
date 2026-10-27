package ci.smartex.offres.service;

import ci.smartex.offres.domain.Dossier;
import ci.smartex.offres.domain.LigneDqe;
import ci.smartex.offres.repo.DepotDossier;
import ci.smartex.offres.repo.DepotLigneDqe;
import ci.smartex.offres.securite.ContexteOrganisation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le chiffrage : l'utilisateur ne tape que les prix unitaires.
 *
 * <p>Designations, unites et quantites viennent du DAO et sont verrouillees.
 * Les montants ne sont jamais ecrits par ce service : ils sont relus apres
 * ecriture, parce que c'est PostgreSQL qui les calcule. Le total fige sur le
 * dossier, lui, est recalcule ici a chaque changement, et c'est ce total que
 * la lettre de soumission portera en chiffres et en lettres.
 */
@Service
public class ServiceDqe {

    private final DepotLigneDqe depotLignes;
    private final DepotDossier depotDossier;
    private final ServiceJournal journal;

    public ServiceDqe(DepotLigneDqe depotLignes,
                      DepotDossier depotDossier,
                      ServiceJournal journal) {
        this.depotLignes = depotLignes;
        this.depotDossier = depotDossier;
        this.journal = journal;
    }

    @Transactional(readOnly = true)
    public List<LigneDqe> lignes(UUID dossierId) {
        return depotLignes.findAllByDossierIdOrderByOrdreSectionAscOrdreAsc(dossierId);
    }

    /**
     * Enregistre les prix unitaires saisis, puis refige le total.
     *
     * @param prix identifiant de ligne vers prix unitaire ; une valeur nulle
     *             efface le prix et remet la ligne en attente
     */
    @Transactional
    public long enregistrerPrix(UUID dossierId, Map<UUID, Long> prix) {
        Dossier dossier = chargerModifiable(dossierId);

        List<LigneDqe> lignes = depotLignes
                .findAllByDossierIdOrderByOrdreSectionAscOrdreAsc(dossierId);

        for (LigneDqe ligne : lignes) {
            if (!prix.containsKey(ligne.getId())) {
                continue;
            }
            Long valeur = prix.get(ligne.getId());
            if (valeur != null && valeur < 0) {
                throw new IllegalArgumentException(
                        "Un prix unitaire ne peut pas etre negatif (ligne "
                                + ligne.getNumeroPrix() + ").");
            }
            ligne.setPrixUnitaire(valeur);
        }

        depotLignes.saveAll(lignes);
        // Le flush rend les montants calcules par la base lisibles tout de
        // suite : sans lui, le total serait fait sur les valeurs d'avant.
        depotLignes.flush();

        return figerTotal(dossier);
    }

    /**
     * Remplace toutes les lignes d'un dossier par celles extraites du DAO.
     * Les prix deja saisis sont perdus : c'est voulu, un bordereau qui change
     * n'est plus le meme bordereau.
     */
    @Transactional
    public void remplacerLignes(UUID dossierId, List<LigneDqe> nouvelles) {
        Dossier dossier = chargerModifiable(dossierId);

        depotLignes.deleteAllByDossierId(dossierId);
        depotLignes.flush();

        UUID organisation = ContexteOrganisation.exigerOrganisation();
        for (LigneDqe ligne : nouvelles) {
            ligne.setOrganisationId(organisation);
            ligne.setDossierId(dossierId);
        }
        depotLignes.saveAll(nouvelles);
        depotLignes.flush();

        figerTotal(dossier);
        journal.enregistrer("DQE_REMPLACE", "dossier", dossierId,
                nouvelles.size() + " ligne(s) issues du DAO");
    }

    /**
     * Recalcule le total et le pose sur le dossier.
     *
     * <p>C'est ce montant, et lui seul, que la lettre de soumission lira. Le
     * controle MONTANT_EN_LETTRES le compare ensuite a la somme des lignes :
     * s'ils divergent, c'est que le PDF date d'avant la derniere saisie.
     */
    @Transactional
    public long figerTotal(Dossier dossier) {
        List<LigneDqe> lignes = depotLignes
                .findAllByDossierIdOrderByOrdreSectionAscOrdreAsc(dossier.getId());

        boolean complet = !lignes.isEmpty()
                && lignes.stream().noneMatch(LigneDqe::prixManquant);

        long total = lignes.stream()
                .mapToLong(LigneDqe::montantOuZero)
                .sum();

        // Tant que tous les prix ne sont pas la, aucun montant n'est fige :
        // une lettre de soumission portant un total partiel serait pire
        // qu'une lettre absente.
        dossier.setMontantSoumission(complet ? total : null);
        dossier.setMajLe(Instant.now());
        depotDossier.save(dossier);

        return total;
    }

    @Transactional(readOnly = true)
    public long nombreDePrixManquants(UUID dossierId) {
        return depotLignes.countByDossierIdAndPrixUnitaireIsNull(dossierId);
    }

    /** Total provisoire, pour l'affichage pendant la saisie. */
    public long totalProvisoire(List<LigneDqe> lignes) {
        return lignes.stream().mapToLong(LigneDqe::montantOuZero).sum();
    }

    public BigDecimal quantiteTotale(List<LigneDqe> lignes) {
        return lignes.stream()
                .map(LigneDqe::getQuantite)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Dossier chargerModifiable(UUID dossierId) {
        Dossier dossier = depotDossier.findById(dossierId).orElseThrow(
                () -> new IllegalArgumentException("Dossier inconnu."));

        if (!dossier.estModifiable()) {
            // Une version validee est figee : la corriger cree une version n+1,
            // elle ne reecrit pas celle qui a ete signee.
            throw new DossierFigeException(
                    "Ce dossier est en " + dossier.getStatut()
                            + " : creez une nouvelle version pour le corriger.");
        }
        return dossier;
    }
}
