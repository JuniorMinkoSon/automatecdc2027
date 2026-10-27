package ci.smartex.offres.config;

import ci.smartex.offres.domain.*;
import ci.smartex.offres.repo.*;
import ci.smartex.offres.securite.ContexteOrganisation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Jeu de demonstration, pour prendre l'application en main.
 *
 * <p>Ne s'execute que si {@code app.demonstration.active} vaut vrai. Ce reglage
 * n'est jamais pose par docker-compose : une installation de production ne
 * creera donc jamais ces comptes, meme par accident.
 *
 * <p>Tout ce qui est cree ici porte la mention « DÉMONSTRATION » dans sa raison
 * sociale. Rien de ce jeu ne doit etre pris pour une donnee reelle : les
 * references, les attestations et les qualifications d'une vraie entreprise ne
 * s'inventent pas, et ce qui est fabrique ici se voit.
 *
 * <p>Le mot de passe est ecrit dans le journal de demarrage. C'est acceptable
 * pour un jeu de demonstration qu'on efface ; cela ne le serait pour aucun
 * compte reel, et c'est pourquoi les vrais comptes passent par une invitation
 * dont l'exploitant ne connait jamais le mot de passe.
 */
@Component
@ConditionalOnProperty(name = "app.demonstration.active", havingValue = "true")
public class AmorcageDemonstration implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AmorcageDemonstration.class);

    private static final String COURRIEL_ADMIN = "admin@demonstration.local";
    private static final String COURRIEL_VALIDATEUR = "validateur@demonstration.local";
    private static final String MOT_DE_PASSE = "Demonstration2026!";

    private final DepotOrganisation depotOrganisation;
    private final DepotUtilisateur depotUtilisateur;
    private final DepotMembre depotMembre;
    private final DepotEntreprise depotEntreprise;
    private final DepotRepresentant depotRepresentant;
    private final DepotPieceAdmin depotPieceAdmin;
    private final DepotPersonnel depotPersonnel;
    private final DepotMateriel depotMateriel;
    private final PasswordEncoder encodeur;

    public AmorcageDemonstration(DepotOrganisation depotOrganisation,
                                 DepotUtilisateur depotUtilisateur,
                                 DepotMembre depotMembre,
                                 DepotEntreprise depotEntreprise,
                                 DepotRepresentant depotRepresentant,
                                 DepotPieceAdmin depotPieceAdmin,
                                 DepotPersonnel depotPersonnel,
                                 DepotMateriel depotMateriel,
                                 PasswordEncoder encodeur) {
        this.depotOrganisation = depotOrganisation;
        this.depotUtilisateur = depotUtilisateur;
        this.depotMembre = depotMembre;
        this.depotEntreprise = depotEntreprise;
        this.depotRepresentant = depotRepresentant;
        this.depotPieceAdmin = depotPieceAdmin;
        this.depotPersonnel = depotPersonnel;
        this.depotMateriel = depotMateriel;
        this.encodeur = encodeur;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (depotUtilisateur.findByEmailIgnoreCase(COURRIEL_ADMIN).isPresent()) {
            annoncer();
            return;
        }

        UUID organisation = creerOrganisation();
        ContexteOrganisation.poser(organisation);
        try {
            creerFiche(organisation);
            creerCoffre(organisation);
            creerMoyens(organisation);

            // Deux comptes, parce qu'une seule personne ne peut pas montrer la
            // separation des taches : celui qui prepare ne valide pas.
            creerCompte(organisation, COURRIEL_ADMIN, "Administrateur de démonstration",
                    Membre.Role.ADMIN_ENTREPRISE);
            creerCompte(organisation, COURRIEL_VALIDATEUR, "Validateur de démonstration",
                    Membre.Role.VALIDATEUR);
        } finally {
            ContexteOrganisation.effacer();
        }

        annoncer();
    }

    private UUID creerOrganisation() {
        ContexteOrganisation.poserSuperAdmin();
        try {
            Organisation organisation = new Organisation();
            organisation.setNom("DÉMONSTRATION — données fictives");
            organisation.setStatut(Organisation.Statut.ACTIVE);
            organisation.setMode(Organisation.Mode.LIBRE_SERVICE);
            return depotOrganisation.save(organisation).getId();
        } finally {
            ContexteOrganisation.effacer();
        }
    }

    private void creerFiche(UUID organisation) {
        Entreprise entreprise = new Entreprise();
        entreprise.setOrganisationId(organisation);
        entreprise.setRaisonSociale("ENTREPRISE DE DÉMONSTRATION (fictive)");
        entreprise.setSigle("DÉMO");
        entreprise.setRccm("CI-DEMO-2026-B-00000");
        entreprise.setCompteContribuable("0000000 D");
        entreprise.setRegimeFiscal("Réel normal");
        entreprise.setAdresse("Abidjan, Cocody");
        entreprise.setBoitePostale("01 BP 0000 Abidjan 01");
        entreprise.setTelephones("+225 00 00 00 00 00");
        entreprise.setEmail("contact@demonstration.local");
        depotEntreprise.save(entreprise);

        Representant gerant = new Representant();
        gerant.setOrganisationId(organisation);
        gerant.setNom("Mme DIALLO Fatou (fictive)");
        gerant.setFonction("Gérante");
        gerant.setSignataireDefaut(true);
        depotRepresentant.save(gerant);
    }

    /**
     * Un coffre deja garni, pour que les controles bloquants passent et qu'on
     * puisse voir un dossier aller jusqu'au bout. Les fichiers eux-memes
     * n'existent pas : ce sont des references, pas des justificatifs. Une piece
     * type vierge ne vaut jamais justificatif.
     */
    private void creerCoffre(UUID organisation) {
        LocalDate valable = LocalDate.now().plusMonths(8);

        for (PieceAdmin.Type type : new PieceAdmin.Type[]{
                PieceAdmin.Type.RCCM, PieceAdmin.Type.DFE,
                PieceAdmin.Type.ATTESTATION_PME, PieceAdmin.Type.ATTESTATION_BANCAIRE,
                PieceAdmin.Type.QUITUS_ARCOP}) {
            depotPieceAdmin.save(piece(organisation, type, valable, null));
        }
        depotPieceAdmin.save(piece(organisation, PieceAdmin.Type.CAUTIONNEMENT,
                valable, 850_000L));

        // Une piece qui expire bientot, pour que le tableau de bord ait quelque
        // chose a signaler : c'est sa raison d'etre.
        depotPieceAdmin.save(piece(organisation, PieceAdmin.Type.ATTESTATION_FISCALE,
                LocalDate.now().plusDays(12), null));
    }

    private void creerMoyens(UUID organisation) {
        depotPersonnel.save(personne(organisation, "KONÉ Ibrahim (fictif)",
                "Conducteur des travaux", "BTS bâtiment", 8));
        depotPersonnel.save(personne(organisation, "YAO Akissi (fictive)",
                "Chef de chantier", "BT bâtiment", 5));

        for (String[] engin : new String[][]{
                {"Bétonnière 350 litres", "2022"},
                {"Aiguille vibrante", "2021"},
                {"Véhicule de liaison", "2020"}}) {
            Materiel materiel = new Materiel();
            materiel.setOrganisationId(organisation);
            materiel.setDesignation(engin[0]);
            materiel.setMarque("Marque fictive");
            materiel.setAnnee(Integer.valueOf(engin[1]));
            materiel.setProvenance(Materiel.Provenance.POSSESSION);
            materiel.setJustificatifChemin("demonstration/justificatif-fictif.pdf");
            depotMateriel.save(materiel);
        }
    }

    private void creerCompte(UUID organisation, String courriel, String nom,
                             Membre.Role role) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setEmail(courriel);
        utilisateur.setNomComplet(nom);
        utilisateur.setMotDePasseHash(encodeur.encode(MOT_DE_PASSE));
        utilisateur.setActif(true);
        UUID identifiant = depotUtilisateur.save(utilisateur).getId();

        Membre membre = new Membre();
        membre.setOrganisationId(organisation);
        membre.setUtilisateurId(identifiant);
        membre.setRole(role);
        depotMembre.save(membre);
    }

    private PieceAdmin piece(UUID organisation, PieceAdmin.Type type,
                             LocalDate expiration, Long montant) {
        PieceAdmin piece = new PieceAdmin();
        piece.setOrganisationId(organisation);
        piece.setType(type);
        piece.setLibelle(type + " (démonstration)");
        piece.setFichierChemin(organisation + "/coffre/demonstration-" + type + ".pdf");
        piece.setDateEmission(LocalDate.now().minusMonths(2));
        piece.setDateExpiration(expiration);
        piece.setMontant(montant);
        piece.setConfirmee(true);
        return piece;
    }

    private Personnel personne(UUID organisation, String nom, String poste,
                               String diplome, int annees) {
        Personnel personne = new Personnel();
        personne.setOrganisationId(organisation);
        personne.setNom(nom);
        personne.setPoste(poste);
        personne.setDiplome(diplome);
        personne.setAnneesExperience(annees);
        personne.setCvChemin("demonstration/cv-fictif.pdf");
        return personne;
    }

    private void annoncer() {
        LOG.warn("""

                ========================================================
                 JEU DE DÉMONSTRATION — DONNÉES FICTIVES
                ========================================================
                 Préparation :  {} / {}
                 Validation  :  {} / {}

                 Les deux comptes sont séparés à dessein : celui qui
                 prépare un dossier ne peut pas le valider.

                 Aucune de ces données n'est réelle. À effacer avant
                 toute mise en service.
                ========================================================
                """, COURRIEL_ADMIN, MOT_DE_PASSE, COURRIEL_VALIDATEUR, MOT_DE_PASSE);
    }
}
