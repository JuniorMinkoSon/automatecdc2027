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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Charge le dossier de reference fourni : l'appel d'offres A0026070727726 du
 * Conseil regional de la Bagoue, et l'entreprise qui y a repondu.
 *
 * <p>Rien n'est invente ici. Chaque valeur vient du dossier de reponse
 * communique : raison sociale, numero d'identification, adresse, gerant,
 * intitule du marche, delais, validites, garantie, sources de financement,
 * personnel et materiel exiges. Ce qui ne figure pas dans ce dossier n'est pas
 * comble : les dates d'expiration des pieces du coffre, les noms du personnel
 * propose et le parc materiel reel restent vides, parce qu'ils etaient
 * absents - le formulaire PER-1 etait blanc dans l'offre deposee.
 *
 * <p>Ces manques ne sont pas un defaut du jeu de donnees : ce sont eux que les
 * controles bloquants doivent faire apparaitre. Un dossier de demonstration ou
 * tout serait vert ne montrerait rien de ce que la plateforme sert a eviter.
 *
 * <p>Ne s'execute que si {@code app.dossier-reference.active} vaut vrai, reglage
 * que docker-compose ne pose jamais.
 */
@Component
@ConditionalOnProperty(name = "app.dossier-reference.active", havingValue = "true")
public class AmorcageDossierReference implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AmorcageDossierReference.class);
    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    /** Le courriel figure dans la fiche de renseignements du dossier de reference. */
    private static final String COURRIEL = "nchtpsarl@gmail.com";

    /**
     * Mot de passe pose pour pouvoir se connecter en local. En service reel,
     * un compte se cree par invitation et son mot de passe n'est connu que de
     * son titulaire : c'est pour cela que le super-administrateur livre par la
     * migration n'en a aucun.
     */
    private static final String MOT_DE_PASSE = "Bagoue2026!";

    private static final String INTITULE =
            "TRAVAUX DE CONSTRUCTION D'UNE CLOTURE DE 300 METRES LINEAIRE, D'UNE HAUTEUR "
            + "DE DEUX (02) METRES + UN INCINERATEUR ET UN BAC A ORDURE DANS DEUX (02) "
            + "CENTRES DE SANTE DE LA REGION (SISSEDOUGOU ET WORA)";

    private final DepotOrganisation depotOrganisation;
    private final DepotUtilisateur depotUtilisateur;
    private final DepotMembre depotMembre;
    private final DepotEntreprise depotEntreprise;
    private final DepotRepresentant depotRepresentant;
    private final DepotAppelOffres depotAppelOffres;
    private final DepotLot depotLot;
    private final DepotExigence depotExigence;
    private final PasswordEncoder encodeur;

    public AmorcageDossierReference(DepotOrganisation depotOrganisation,
                                    DepotUtilisateur depotUtilisateur,
                                    DepotMembre depotMembre,
                                    DepotEntreprise depotEntreprise,
                                    DepotRepresentant depotRepresentant,
                                    DepotAppelOffres depotAppelOffres,
                                    DepotLot depotLot,
                                    DepotExigence depotExigence,
                                    PasswordEncoder encodeur) {
        this.depotOrganisation = depotOrganisation;
        this.depotUtilisateur = depotUtilisateur;
        this.depotMembre = depotMembre;
        this.depotEntreprise = depotEntreprise;
        this.depotRepresentant = depotRepresentant;
        this.depotAppelOffres = depotAppelOffres;
        this.depotLot = depotLot;
        this.depotExigence = depotExigence;
        this.encodeur = encodeur;
    }

    /**
     * Volontairement hors transaction unique.
     *
     * <p>Hibernate resout l'organisation a l'ouverture de la session, pas a
     * chaque requete : dans une seule transaction, changer de contexte en
     * cours de route n'a aucun effet, et les ecritures partent toutes avec
     * l'organisation du debut. Elles sont alors refusees par la Row-Level
     * Security au moment du flush, c'est-a-dire a la validation - loin de
     * l'endroit ou l'erreur a ete commise.
     *
     * <p>Chaque enregistrement se fait donc dans sa propre transaction, ce qui
     * ouvre une session neuve et relit le contexte courant.
     */
    @Override
    public void run(ApplicationArguments arguments) {
        if (depotUtilisateur.findByEmailIgnoreCase(COURRIEL).isPresent()) {
            annoncer();
            return;
        }

        UUID organisation = creerOrganisation();
        ContexteOrganisation.poser(organisation);
        try {
            creerEntreprise(organisation);
            creerCompte(organisation);
            UUID appelOffres = creerAppelOffres(organisation);
            creerLots(organisation, appelOffres);
            creerExigences(organisation, appelOffres);
        } finally {
            ContexteOrganisation.effacer();
        }
        annoncer();
    }

    private UUID creerOrganisation() {
        ContexteOrganisation.poserSuperAdmin();
        try {
            Organisation organisation = new Organisation();
            organisation.setNom("NCHTP");
            organisation.setStatut(Organisation.Statut.ACTIVE);
            organisation.setMode(Organisation.Mode.LIBRE_SERVICE);
            return depotOrganisation.save(organisation).getId();
        } finally {
            ContexteOrganisation.effacer();
        }
    }

    /** Tiré du formulaire de renseignements sur le candidat. */
    private void creerEntreprise(UUID organisation) {
        Entreprise entreprise = new Entreprise();
        entreprise.setOrganisationId(organisation);
        entreprise.setRaisonSociale("NCHTP");
        entreprise.setSigle("NCHTP");
        // Numero d'identification de l'entreprise, enregistree en 2025.
        entreprise.setRccm("CI-BAS-01-2025-B13-00258");
        entreprise.setAdresse("Grand-Bassam, quartier Impérial");
        entreprise.setBoitePostale("16 BP 1620 Abidjan 16");
        entreprise.setTelephones("07 07 95 75 74");
        entreprise.setEmail(COURRIEL);
        depotEntreprise.save(entreprise);

        Representant gerant = new Representant();
        gerant.setOrganisationId(organisation);
        gerant.setNom("M. GANA ABDOULAYE");
        gerant.setFonction("Gérant");
        gerant.setSignataireDefaut(true);
        depotRepresentant.save(gerant);
    }

    private void creerCompte(UUID organisation) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setEmail(COURRIEL);
        utilisateur.setNomComplet("GANA ABDOULAYE");
        utilisateur.setMotDePasseHash(encodeur.encode(MOT_DE_PASSE));
        utilisateur.setActif(true);
        UUID identifiant = depotUtilisateur.save(utilisateur).getId();

        Membre membre = new Membre();
        membre.setOrganisationId(organisation);
        membre.setUtilisateurId(identifiant);
        membre.setRole(Membre.Role.ADMIN_ENTREPRISE);
        depotMembre.save(membre);
    }

    private UUID creerAppelOffres(UUID organisation) {
        AppelOffres appel = new AppelOffres();
        appel.setOrganisationId(organisation);
        appel.setNumeroAo("A0026070727726");
        appel.setIntitule(INTITULE);
        appel.setAutoriteContractante("CONSEIL RÉGIONAL DE LA BAGOUÉ");
        appel.setMaitreOuvrage("CONSEIL RÉGIONAL DE LA BAGOUÉ");
        appel.setDestinataire("CONSEIL RÉGIONAL DE LA BAGOUÉ");
        appel.setFinancement("Fonds propres (20 000 000 F CFA) et Versus Bank (6 250 000 F CFA)");
        appel.setValiditeOffreJours(120);

        // Le dossier de reference ne precise pas le regime de prix ni le sort
        // de la TVA : les laisser nuls fait apparaitre l'avertissement, qui
        // est precisement la question a trancher.
        appel.setDateLimiteDepot(ZonedDateTime.of(2026, 8, 14, 23, 59, 0, 0, ABIDJAN)
                .toInstant());
        appel.setStatut(AppelOffres.Statut.A_VERIFIER);
        appel.setErreurAnalyse("Régime de prix et sort de la TVA non précisés par le "
                + "dossier de référence : à compléter avant génération.");
        return depotAppelOffres.save(appel).getId();
    }

    /** Les deux centres de sante de la region, quatre mois par lot. */
    private void creerLots(UUID organisation, UUID appelOffres) {
        String[] localites = {"SISSÉDOUGOU", "WORA"};
        for (int index = 0; index < localites.length; index++) {
            Lot lot = new Lot();
            lot.setOrganisationId(organisation);
            lot.setAppelOffresId(appelOffres);
            lot.setNumero(index + 1);
            lot.setIntitule("Centre de santé de " + localites[index]);
            lot.setLocalite(localites[index]);
            lot.setDelaiMois(4);
            lot.setMontantGarantie(850_000L);
            depotLot.save(lot);
        }
    }

    /**
     * Personnel et materiel exiges par le dossier d'appel d'offres. Ce sont des
     * exigences, pas des moyens : le personnel propose et le parc reel de
     * l'entreprise restent a saisir, et leur absence fera echouer les controles
     * bloquants - comme elle aurait du le faire sur le formulaire PER-1 laisse
     * blanc dans l'offre deposee.
     */
    private void creerExigences(UUID organisation, UUID appelOffres) {
        creerExigence(organisation, appelOffres, Exigence.Type.PERSONNEL,
                "Conducteur des travaux", "BTS bâtiment, 5 ans d'expérience", null, 5);
        creerExigence(organisation, appelOffres, Exigence.Type.PERSONNEL,
                "Chef de chantier", "BT bâtiment, 3 ans d'expérience", null, 3);

        creerExigence(organisation, appelOffres, Exigence.Type.MATERIEL,
                "Bétonnière", "Nombre minimum : 01", 1, null);
        creerExigence(organisation, appelOffres, Exigence.Type.MATERIEL,
                "Aiguille vibrante", "Nombre minimum : 01", 1, null);
        creerExigence(organisation, appelOffres, Exigence.Type.MATERIEL,
                "Véhicule de liaison", "Nombre minimum : 01", 1, null);

        for (String piece : new String[]{
                "Cautionnement provisoire", "Registre de commerce",
                "Déclaration fiscale d'existence", "Attestation PME",
                "Quitus ARCOP", "Attestation bancaire",
                "Attestation de préfinancement bancaire"}) {
            creerExigence(organisation, appelOffres, Exigence.Type.PIECE, piece, null, null, null);
        }
    }

    private void creerExigence(UUID organisation, UUID appelOffres, Exigence.Type type,
                               String libelle, String valeur,
                               Integer quantiteMin, Integer anneesMin) {
        Exigence exigence = new Exigence();
        exigence.setOrganisationId(organisation);
        exigence.setAppelOffresId(appelOffres);
        exigence.setType(type);
        exigence.setLibelle(libelle);
        exigence.setValeur(valeur);
        exigence.setQuantiteMin(quantiteMin);
        exigence.setAnneesMin(anneesMin);
        exigence.setEliminatoire(true);
        exigence.setSourceDao("Dossier de référence A0026070727726");
        depotExigence.save(exigence);
    }

    private void annoncer() {
        LOG.warn("""

                ========================================================
                 DOSSIER DE RÉFÉRENCE CHARGÉ — A0026070727726
                ========================================================
                 Connexion : {} / {}
                 Rôle      : administrateur d'entreprise

                 Deux lots sont prêts : Sissédougou et Wora.

                 Le coffre, le personnel proposé et le parc matériel
                 sont VIDES : ils ne figuraient pas dans le dossier
                 communiqué, et rien n'a été inventé pour les combler.
                 Les contrôles bloquants vont donc les signaler — c'est
                 exactement ce qu'ils doivent faire.

                 La validation demandera un second compte : celui qui
                 a préparé un dossier ne peut pas le valider.
                ========================================================
                """, COURRIEL, MOT_DE_PASSE);
    }
}
