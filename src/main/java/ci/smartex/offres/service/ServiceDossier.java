package ci.smartex.offres.service;

import ci.smartex.offres.conformite.MoteurConformite;
import ci.smartex.offres.conformite.RapportConformite;
import ci.smartex.offres.domain.AppelOffres;
import ci.smartex.offres.domain.Dossier;
import ci.smartex.offres.repo.*;
import ci.smartex.offres.securite.ContexteOrganisation;
import ci.smartex.offres.securite.UtilisateurConnecte;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Le circuit d'un dossier : brouillon, en validation, valide, depose.
 *
 * <p>Deux regles tiennent tout le reste :
 * <ul>
 *   <li>celui qui a modifie un dossier ne peut pas le valider ;</li>
 *   <li>un dossier dont un controle bloquant echoue ne part pas en validation.</li>
 * </ul>
 *
 * <p>Les deux sont verifiees ici, cote serveur. L'interface grise aussi les
 * boutons, mais c'est un confort : une interface ne protege rien.
 */
@Service
public class ServiceDossier {

    private final DepotDossier depotDossier;
    private final DepotAppelOffres depotAppelOffres;
    private final DepotLot depotLot;
    private final DepotRepresentant depotRepresentant;
    private final DepotLigneDqe depotLigneDqe;
    private final DepotAffectationPersonnel depotAffectationPersonnel;
    private final DepotAffectationMateriel depotAffectationMateriel;
    private final MoteurConformite moteur;
    private final ServiceJournal journal;

    public ServiceDossier(DepotDossier depotDossier,
                          DepotAppelOffres depotAppelOffres,
                          DepotLot depotLot,
                          DepotRepresentant depotRepresentant,
                          DepotLigneDqe depotLigneDqe,
                          DepotAffectationPersonnel depotAffectationPersonnel,
                          DepotAffectationMateriel depotAffectationMateriel,
                          MoteurConformite moteur,
                          ServiceJournal journal) {
        this.depotDossier = depotDossier;
        this.depotAppelOffres = depotAppelOffres;
        this.depotLot = depotLot;
        this.depotRepresentant = depotRepresentant;
        this.depotLigneDqe = depotLigneDqe;
        this.depotAffectationPersonnel = depotAffectationPersonnel;
        this.depotAffectationMateriel = depotAffectationMateriel;
        this.moteur = moteur;
        this.journal = journal;
    }

    /**
     * Cree le dossier d'un lot, en reprenant d'office les engagements que le
     * DAO impose : delai du lot, validite de l'offre, validite de la garantie.
     * Les trois controles bloquants correspondants passent donc des le depart,
     * et l'utilisateur n'a a y toucher que s'il veut s'engager sur moins.
     */
    @Transactional
    public Dossier creer(UUID appelOffresId, UUID lotId) {
        UUID organisation = ContexteOrganisation.exigerOrganisation();

        AppelOffres appelOffres = depotAppelOffres.findById(appelOffresId).orElseThrow(
                () -> new IllegalArgumentException("Appel d'offres inconnu."));

        Dossier dossier = new Dossier();
        dossier.setOrganisationId(organisation);
        dossier.setAppelOffresId(appelOffresId);
        dossier.setLotId(lotId);
        dossier.setStatut(Dossier.Statut.BROUILLON);
        dossier.setVersion(prochaineVersion(appelOffresId, lotId));
        dossier.setAuteurId(utilisateurCourant());
        dossier.setModifiePar(utilisateurCourant());

        if (lotId != null) {
            depotLot.findById(lotId).ifPresent(lot -> dossier.setDelaiExecutionMois(lot.getDelaiMois()));
        }
        dossier.setValiditeOffreJours(appelOffres.getValiditeOffreJours());
        if (appelOffres.getValiditeOffreJours() != null) {
            dossier.setValiditeGarantieJours(appelOffres.getValiditeOffreJours() + 30);
        }

        depotRepresentant.findFirstByOrganisationIdAndSignataireDefautTrue(organisation)
                .ifPresent(representant -> dossier.setRepresentantId(representant.getId()));

        Dossier enregistre = depotDossier.save(dossier);
        journal.enregistrer("DOSSIER_CREE", "dossier", enregistre.getId(),
                "appel d'offres " + appelOffresId);
        return enregistre;
    }

    @Transactional(readOnly = true)
    public Dossier charger(UUID dossierId) {
        return depotDossier.findById(dossierId).orElseThrow(
                () -> new IllegalArgumentException("Dossier inconnu."));
    }

    @Transactional
    public Dossier enregistrerEngagements(UUID dossierId,
                                          Integer delaiMois,
                                          Integer validiteOffre,
                                          Integer validiteGarantie,
                                          UUID representantId) {
        Dossier dossier = chargerModifiable(dossierId);
        dossier.setDelaiExecutionMois(delaiMois);
        dossier.setValiditeOffreJours(validiteOffre);
        dossier.setValiditeGarantieJours(validiteGarantie);
        dossier.setRepresentantId(representantId);
        dossier.setModifiePar(utilisateurCourant());
        dossier.setMajLe(Instant.now());

        Dossier enregistre = depotDossier.save(dossier);
        journal.enregistrer("ENGAGEMENTS_MODIFIES", "dossier", dossierId);
        return enregistre;
    }

    /**
     * Soumet le dossier a validation, si et seulement si aucun controle
     * bloquant n'echoue.
     */
    @Transactional
    public RapportConformite soumettreAValidation(UUID dossierId) {
        Dossier dossier = chargerModifiable(dossierId);
        RapportConformite rapport = moteur.executer(dossierId);

        if (!rapport.validationPossible()) {
            journal.enregistrer("SOUMISSION_REFUSEE", "dossier", dossierId,
                    rapport.nombreBloquants() + " controle(s) bloquant(s)");
            throw new ControleBloquantException(
                    "Le dossier ne peut pas partir en validation : "
                            + rapport.nombreBloquants() + " controle(s) bloquant(s) echouent.",
                    rapport);
        }

        dossier.setStatut(Dossier.Statut.EN_VALIDATION);
        dossier.setMajLe(Instant.now());
        depotDossier.save(dossier);

        journal.enregistrer("SOUMIS_A_VALIDATION", "dossier", dossierId);
        return rapport;
    }

    /**
     * Valide et verrouille la version.
     *
     * <p>Refuse si le validateur est celui qui a modifie le dossier en dernier.
     * La separation des taches ne vaut que si personne ne peut se relire
     * soi-meme.
     */
    @Transactional
    public Dossier valider(UUID dossierId, String commentaire) {
        Dossier dossier = charger(dossierId);

        if (dossier.getStatut() != Dossier.Statut.EN_VALIDATION) {
            throw new IllegalStateException(
                    "Seul un dossier en validation peut etre valide.");
        }

        UUID validateur = utilisateurCourant();
        if (validateur != null && validateur.equals(dossier.getModifiePar())) {
            throw new SeparationDesTachesException(
                    "Vous avez modifie ce dossier : sa validation revient a quelqu'un d'autre.");
        }

        // Dernier passage des controles avant de figer : entre la soumission et
        // la validation, une piece du coffre a pu expirer.
        RapportConformite rapport = moteur.executer(dossierId);
        if (!rapport.validationPossible()) {
            throw new ControleBloquantException(
                    "Un controle bloquant a echoue depuis la soumission. Renvoyez en brouillon.",
                    rapport);
        }

        dossier.setStatut(Dossier.Statut.VALIDE);
        dossier.setValidateurId(validateur);
        dossier.setCommentaireValidation(commentaire);
        dossier.setValideLe(Instant.now());
        dossier.setMajLe(Instant.now());

        Dossier valide = depotDossier.save(dossier);
        journal.enregistrer("DOSSIER_VALIDE", "dossier", dossierId,
                "version " + dossier.getVersion());
        return valide;
    }

    /** Renvoie en brouillon avec les commentaires du validateur, qui sont obligatoires. */
    @Transactional
    public Dossier renvoyerEnBrouillon(UUID dossierId, String commentaire) {
        if (commentaire == null || commentaire.isBlank()) {
            throw new IllegalArgumentException(
                    "Un renvoi en brouillon sans commentaire n'apprend rien au redacteur.");
        }

        Dossier dossier = charger(dossierId);
        if (dossier.getStatut() != Dossier.Statut.EN_VALIDATION) {
            throw new IllegalStateException("Ce dossier n'est pas en validation.");
        }

        dossier.setStatut(Dossier.Statut.BROUILLON);
        dossier.setCommentaireValidation(commentaire);
        dossier.setMajLe(Instant.now());

        Dossier renvoye = depotDossier.save(dossier);
        journal.enregistrer("RENVOYE_EN_BROUILLON", "dossier", dossierId, commentaire);
        return renvoye;
    }

    @Transactional
    public Dossier marquerDepose(UUID dossierId) {
        Dossier dossier = charger(dossierId);
        if (dossier.getStatut() != Dossier.Statut.VALIDE) {
            throw new IllegalStateException("Seul un dossier valide peut etre marque depose.");
        }
        dossier.setStatut(Dossier.Statut.DEPOSE);
        dossier.setDeposeLe(Instant.now());
        dossier.setMajLe(Instant.now());

        Dossier depose = depotDossier.save(dossier);
        journal.enregistrer("DOSSIER_DEPOSE", "dossier", dossierId);
        return depose;
    }

    /**
     * Cree une version n+1 a partir d'une version figee, en recopiant les
     * lignes et les affectations. L'ancienne version reste intacte, avec son
     * PDF et son empreinte : c'est elle qui a ete signee.
     */
    @Transactional
    public Dossier nouvelleVersion(UUID dossierId) {
        Dossier precedent = charger(dossierId);
        if (!precedent.getStatut().estFige()) {
            throw new IllegalStateException(
                    "Ce dossier est encore modifiable : inutile d'en creer une version.");
        }

        Dossier suivant = creer(precedent.getAppelOffresId(), precedent.getLotId());
        suivant.setDelaiExecutionMois(precedent.getDelaiExecutionMois());
        suivant.setValiditeOffreJours(precedent.getValiditeOffreJours());
        suivant.setValiditeGarantieJours(precedent.getValiditeGarantieJours());
        suivant.setRepresentantId(precedent.getRepresentantId());
        depotDossier.save(suivant);

        UUID organisation = ContexteOrganisation.exigerOrganisation();

        depotLigneDqe.findAllByDossierIdOrderByOrdreSectionAscOrdreAsc(dossierId)
                .forEach(source -> {
                    var copie = new ci.smartex.offres.domain.LigneDqe();
                    copie.setOrganisationId(organisation);
                    copie.setDossierId(suivant.getId());
                    copie.setSection(source.getSection());
                    copie.setOrdreSection(source.getOrdreSection());
                    copie.setNumeroPrix(source.getNumeroPrix());
                    copie.setOrdre(source.getOrdre());
                    copie.setDesignation(source.getDesignation());
                    copie.setUnite(source.getUnite());
                    copie.setQuantite(source.getQuantite());
                    copie.setPrixUnitaire(source.getPrixUnitaire());
                    copie.setIssueDuDao(source.isIssueDuDao());
                    depotLigneDqe.save(copie);
                });

        depotAffectationPersonnel.findAllByDossierId(dossierId).forEach(source -> {
            var copie = new ci.smartex.offres.domain.AffectationPersonnel();
            copie.setOrganisationId(organisation);
            copie.setDossierId(suivant.getId());
            copie.setPersonnelId(source.getPersonnelId());
            copie.setExigenceId(source.getExigenceId());
            copie.setPosteExige(source.getPosteExige());
            copie.setProposeAutomatiquement(source.isProposeAutomatiquement());
            depotAffectationPersonnel.save(copie);
        });

        depotAffectationMateriel.findAllByDossierId(dossierId).forEach(source -> {
            var copie = new ci.smartex.offres.domain.AffectationMateriel();
            copie.setOrganisationId(organisation);
            copie.setDossierId(suivant.getId());
            copie.setMaterielId(source.getMaterielId());
            copie.setExigenceId(source.getExigenceId());
            copie.setDesignationExigee(source.getDesignationExigee());
            copie.setProposeAutomatiquement(source.isProposeAutomatiquement());
            depotAffectationMateriel.save(copie);
        });

        journal.enregistrer("NOUVELLE_VERSION", "dossier", suivant.getId(),
                "a partir de la version " + precedent.getVersion());
        return suivant;
    }

    @Transactional(readOnly = true)
    public List<Dossier> dossiersEnCours() {
        return depotDossier.findAllByOrganisationIdOrderByMajLeDesc(
                ContexteOrganisation.exigerOrganisation());
    }

    private int prochaineVersion(UUID appelOffresId, UUID lotId) {
        return depotDossier.findAllByAppelOffresId(appelOffresId).stream()
                .filter(autre -> lotId == null
                        ? autre.getLotId() == null
                        : lotId.equals(autre.getLotId()))
                .mapToInt(Dossier::getVersion)
                .max()
                .orElse(0) + 1;
    }

    private Dossier chargerModifiable(UUID dossierId) {
        Dossier dossier = charger(dossierId);
        if (!dossier.estModifiable()) {
            throw new DossierFigeException(
                    "Ce dossier est en " + dossier.getStatut()
                            + " : creez une nouvelle version pour le corriger.");
        }
        return dossier;
    }

    private UUID utilisateurCourant() {
        var authentification = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentification != null
                && authentification.getPrincipal() instanceof UtilisateurConnecte utilisateur) {
            return utilisateur.getUtilisateurId();
        }
        return null;
    }
}
