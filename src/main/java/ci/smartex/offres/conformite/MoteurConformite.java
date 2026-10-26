package ci.smartex.offres.conformite;

import ci.smartex.offres.domain.*;
import ci.smartex.offres.repo.*;
import ci.smartex.offres.securite.ContexteOrganisation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Execute tous les controles sur un dossier et archive leur resultat.
 *
 * <p>Un dossier ne peut pas etre valide tant qu'un controle bloquant echoue.
 * Cette regle est appliquee ici, cote serveur, et pas seulement en grisant un
 * bouton : l'interface masque, elle ne protege pas.
 *
 * <p>Un controle qui leve une exception est compte comme echoue plutot
 * qu'ignore. Une regle cassee ne doit pas ouvrir la porte en se taisant.
 */
@Service
public class MoteurConformite {

    private static final Logger LOG = LoggerFactory.getLogger(MoteurConformite.class);
    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final List<Controle> controles;
    private final DepotDossier depotDossier;
    private final DepotAppelOffres depotAppelOffres;
    private final DepotLot depotLot;
    private final DepotEntreprise depotEntreprise;
    private final DepotExigence depotExigence;
    private final DepotLigneDqe depotLigneDqe;
    private final DepotPieceAdmin depotPieceAdmin;
    private final DepotPersonnel depotPersonnel;
    private final DepotMateriel depotMateriel;
    private final DepotAffectationPersonnel depotAffectationPersonnel;
    private final DepotAffectationMateriel depotAffectationMateriel;
    private final DepotControleResultat depotControleResultat;

    public MoteurConformite(List<Controle> controles,
                            DepotDossier depotDossier,
                            DepotAppelOffres depotAppelOffres,
                            DepotLot depotLot,
                            DepotEntreprise depotEntreprise,
                            DepotExigence depotExigence,
                            DepotLigneDqe depotLigneDqe,
                            DepotPieceAdmin depotPieceAdmin,
                            DepotPersonnel depotPersonnel,
                            DepotMateriel depotMateriel,
                            DepotAffectationPersonnel depotAffectationPersonnel,
                            DepotAffectationMateriel depotAffectationMateriel,
                            DepotControleResultat depotControleResultat) {
        this.controles = controles.stream()
                .sorted(Comparator.comparing(Controle::niveau).thenComparing(Controle::code))
                .toList();
        this.depotDossier = depotDossier;
        this.depotAppelOffres = depotAppelOffres;
        this.depotLot = depotLot;
        this.depotEntreprise = depotEntreprise;
        this.depotExigence = depotExigence;
        this.depotLigneDqe = depotLigneDqe;
        this.depotPieceAdmin = depotPieceAdmin;
        this.depotPersonnel = depotPersonnel;
        this.depotMateriel = depotMateriel;
        this.depotAffectationPersonnel = depotAffectationPersonnel;
        this.depotAffectationMateriel = depotAffectationMateriel;
        this.depotControleResultat = depotControleResultat;
    }

    /** Relance tous les controles et remplace le rapport precedent. */
    @Transactional
    public RapportConformite executer(UUID dossierId) {
        DossierAControler aControler = rassembler(dossierId);
        List<ControleResultat> resultats = new ArrayList<>();

        for (Controle controle : controles) {
            if (!sApplique(controle, aControler)) {
                continue;
            }
            resultats.add(executerUn(controle, aControler));
        }

        depotControleResultat.deleteAllByDossierId(dossierId);
        depotControleResultat.saveAll(resultats);

        return new RapportConformite(dossierId, resultats);
    }

    /** Le rapport deja calcule, sans relancer les controles. */
    @Transactional(readOnly = true)
    public RapportConformite dernierRapport(UUID dossierId) {
        return new RapportConformite(dossierId,
                depotControleResultat.findAllByDossierIdOrderByNiveauAscCodeAsc(dossierId));
    }

    private boolean sApplique(Controle controle, DossierAControler aControler) {
        try {
            return controle.sApplique(aControler);
        } catch (RuntimeException erreur) {
            LOG.warn("Le controle {} n'a pas su dire s'il s'appliquait ; il sera execute.",
                    controle.code(), erreur);
            return true;
        }
    }

    private ControleResultat executerUn(Controle controle, DossierAControler aControler) {
        Verdict verdict;
        try {
            verdict = controle.verifier(aControler);
        } catch (RuntimeException erreur) {
            // Une regle qui casse compte comme non satisfaite : le silence
            // d'un controle ne doit jamais valoir autorisation.
            LOG.error("Le controle {} a echoue sur le dossier {}.",
                    controle.code(), aControler.dossier().getId(), erreur);
            verdict = Verdict.echec("Ce controle n'a pas pu s'executer. "
                    + "Signalez-le avant de deposer.");
        }

        ControleResultat resultat = new ControleResultat();
        resultat.setOrganisationId(aControler.dossier().getOrganisationId());
        resultat.setDossierId(aControler.dossier().getId());
        resultat.setCode(controle.code());
        resultat.setLibelle(controle.libelle());
        resultat.setNiveau(controle.niveau());
        resultat.setReussi(verdict.reussi());
        resultat.setMessage(verdict.message());
        resultat.setLienCorrection(verdict.lienCorrection());
        return resultat;
    }

    /**
     * Rassemble tout ce que les controles ont le droit de voir, en une fois.
     * C'est ce qui leur permet d'etre de simples fonctions, testables sans
     * base de donnees.
     */
    private DossierAControler rassembler(UUID dossierId) {
        Dossier dossier = depotDossier.findById(dossierId).orElseThrow(
                () -> new IllegalArgumentException("Dossier inconnu : " + dossierId));

        AppelOffres appelOffres = depotAppelOffres.findById(dossier.getAppelOffresId())
                .orElseThrow(() -> new IllegalStateException(
                        "Le dossier reference un appel d'offres introuvable."));

        Optional<Lot> lot = Optional.ofNullable(dossier.getLotId()).flatMap(depotLot::findById);

        Entreprise entreprise = depotEntreprise
                .findByOrganisationId(ContexteOrganisation.exigerOrganisation())
                .orElseThrow(() -> new IllegalStateException(
                        "La fiche entreprise n'est pas renseignee."));

        List<Dossier> autresLots = depotDossier.findAllByAppelOffresId(appelOffres.getId()).stream()
                .filter(autre -> !autre.getId().equals(dossierId))
                .toList();

        List<AffectationPersonnel> affectationsAutresLots = autresLots.stream()
                .flatMap(autre -> depotAffectationPersonnel
                        .findAllByDossierId(autre.getId()).stream())
                .toList();

        List<Long> totauxAutresLots = autresLots.stream()
                .map(autre -> autre.getMontantSoumission())
                .filter(montant -> montant != null)
                .toList();

        // La conformite se juge a la date limite de depot : une attestation
        // valable aujourd'hui mais perimee ce jour-la ne vaut rien.
        LocalDate dateReference = appelOffres.getDateLimiteDepot() == null
                ? LocalDate.now(ABIDJAN)
                : appelOffres.getDateLimiteDepot().atZone(ABIDJAN).toLocalDate();

        return new DossierAControler(
                dossier,
                appelOffres,
                lot,
                entreprise,
                depotExigence.findAllByAppelOffresId(appelOffres.getId()),
                depotLigneDqe.findAllByDossierIdOrderByOrdreSectionAscOrdreAsc(dossierId),
                depotPieceAdmin.findAllByOrganisationId(dossier.getOrganisationId()),
                depotPersonnel.findAllByOrganisationId(dossier.getOrganisationId()),
                depotMateriel.findAllByOrganisationId(dossier.getOrganisationId()),
                depotAffectationPersonnel.findAllByDossierId(dossierId),
                depotAffectationMateriel.findAllByDossierId(dossierId),
                autresLots,
                affectationsAutresLots,
                totauxAutresLots,
                dateReference);
    }
}
