package ci.smartex.offres.config;

import ci.smartex.offres.domain.AppelOffres;
import ci.smartex.offres.domain.Entreprise;
import ci.smartex.offres.domain.Exigence;
import ci.smartex.offres.domain.Lot;
import ci.smartex.offres.domain.Membre;
import ci.smartex.offres.domain.Organisation;
import ci.smartex.offres.domain.Representant;
import ci.smartex.offres.domain.Utilisateur;
import ci.smartex.offres.repo.DepotAppelOffres;
import ci.smartex.offres.repo.DepotEntreprise;
import ci.smartex.offres.repo.DepotExigence;
import ci.smartex.offres.repo.DepotLot;
import ci.smartex.offres.repo.DepotMembre;
import ci.smartex.offres.repo.DepotOrganisation;
import ci.smartex.offres.repo.DepotRepresentant;
import ci.smartex.offres.repo.DepotUtilisateur;
import ci.smartex.offres.securite.ContexteOrganisation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Charge un dossier de reference dans une organisation neuve, pour travailler
 * sur un cas reel plutot que sur des donnees inventees.
 *
 * <p>Le contenu vient d'un fichier, jamais du code. Une raison sociale, un
 * registre de commerce, un numero de telephone ou un mot de passe ecrits en
 * dur partiraient dans Git a la premiere validation, et y resteraient : le
 * depot deviendrait le document le plus sensible du projet. Le fichier reste
 * sur la machine qui en a besoin, sous {@code donnees/}, que Git ignore.
 *
 * <p>Rien n'est complete d'office. Le coffre, le personnel propose et le parc
 * materiel restent tels que le fichier les laisse, c'est-a-dire le plus
 * souvent vides : ces manques ne sont pas un defaut du jeu de donnees, ce sont
 * eux que les controles bloquants doivent faire apparaitre. Un dossier ou tout
 * serait vert ne montrerait rien de ce que la plateforme sert a eviter.
 *
 * <p>Ne s'execute que si {@code app.dossier-reference.active} vaut vrai,
 * reglage que docker-compose ne pose jamais.
 */
@Component
@ConditionalOnProperty(name = "app.dossier-reference.active", havingValue = "true")
public class AmorcageDossierReference implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AmorcageDossierReference.class);
    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final Path fichier;
    private final ObjectMapper json;
    private final DepotOrganisation depotOrganisation;
    private final DepotUtilisateur depotUtilisateur;
    private final DepotMembre depotMembre;
    private final DepotEntreprise depotEntreprise;
    private final DepotRepresentant depotRepresentant;
    private final DepotAppelOffres depotAppelOffres;
    private final DepotLot depotLot;
    private final DepotExigence depotExigence;
    private final PasswordEncoder encodeur;

    public AmorcageDossierReference(
            @Value("${app.dossier-reference.fichier:./donnees/dossier-reference.json}")
            String chemin,
            ObjectMapper json,
            DepotOrganisation depotOrganisation,
            DepotUtilisateur depotUtilisateur,
            DepotMembre depotMembre,
            DepotEntreprise depotEntreprise,
            DepotRepresentant depotRepresentant,
            DepotAppelOffres depotAppelOffres,
            DepotLot depotLot,
            DepotExigence depotExigence,
            PasswordEncoder encodeur) {
        this.fichier = Path.of(chemin);
        this.json = json;
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
        DossierReference reference = lire();
        if (reference == null) {
            return;
        }

        String courriel = reference.compte().courriel();
        if (depotUtilisateur.findByEmailIgnoreCase(courriel).isPresent()) {
            annoncer(reference);
            return;
        }

        UUID organisation = creerOrganisation(reference);
        ContexteOrganisation.poser(organisation);
        try {
            creerEntreprise(organisation, reference);
            creerCompte(organisation, reference.compte());
            UUID appelOffres = creerAppelOffres(organisation, reference.appelOffres());
            creerLots(organisation, appelOffres, reference.lots());
            creerExigences(organisation, appelOffres, reference);
        } finally {
            ContexteOrganisation.effacer();
        }
        annoncer(reference);
    }

    // ------------------------------------------------------------------ lecture

    /**
     * Rend {@code null} et dit pourquoi plutot que d'empecher le demarrage :
     * un dossier de reference absent n'est pas une panne de la plateforme, et
     * l'application sert sans lui.
     */
    private DossierReference lire() {
        if (!Files.isRegularFile(fichier)) {
            LOG.warn("""

                    Le chargement du dossier de reference est demande, mais le
                    fichier {} n'existe pas. Rien n'a ete charge : la plateforme
                    demarre vide. Le modele a recopier est docs/dossier-reference.exemple.json.
                    """, fichier.toAbsolutePath());
            return null;
        }
        try {
            DossierReference reference =
                    json.readValue(Files.readAllBytes(fichier), DossierReference.class);
            String manque = reference.premierManque();
            if (manque != null) {
                LOG.error("Le dossier de reference {} est incomplet : {} manque. "
                        + "Rien n'a ete charge.", fichier.toAbsolutePath(), manque);
                return null;
            }
            return reference;
        } catch (IOException erreur) {
            LOG.error("Le dossier de reference {} n'est pas lisible. Rien n'a ete charge.",
                    fichier.toAbsolutePath(), erreur);
            return null;
        }
    }

    // ------------------------------------------------------------------ ecriture

    private UUID creerOrganisation(DossierReference reference) {
        ContexteOrganisation.poserSuperAdmin();
        try {
            Organisation organisation = new Organisation();
            organisation.setNom(reference.organisation() == null || reference.organisation().isBlank()
                    ? reference.entreprise().raisonSociale()
                    : reference.organisation());
            organisation.setStatut(Organisation.Statut.ACTIVE);
            organisation.setMode(Organisation.Mode.LIBRE_SERVICE);
            return depotOrganisation.save(organisation).getId();
        } finally {
            ContexteOrganisation.effacer();
        }
    }

    private void creerEntreprise(UUID organisation, DossierReference reference) {
        DossierReference.Entreprise source = reference.entreprise();
        Entreprise entreprise = new Entreprise();
        entreprise.setOrganisationId(organisation);
        entreprise.setRaisonSociale(source.raisonSociale());
        entreprise.setSigle(source.sigle());
        entreprise.setRccm(source.rccm());
        entreprise.setAdresse(source.adresse());
        entreprise.setBoitePostale(source.boitePostale());
        entreprise.setTelephones(source.telephones());
        entreprise.setEmail(source.email() == null
                ? reference.compte().courriel()
                : source.email());
        depotEntreprise.save(entreprise);

        if (reference.representant() == null) {
            return;
        }
        Representant signataire = new Representant();
        signataire.setOrganisationId(organisation);
        signataire.setNom(reference.representant().nom());
        signataire.setFonction(reference.representant().fonction());
        signataire.setSignataireDefaut(true);
        depotRepresentant.save(signataire);
    }

    private void creerCompte(UUID organisation, DossierReference.Compte compte) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setEmail(compte.courriel());
        utilisateur.setNomComplet(compte.nomComplet());
        utilisateur.setMotDePasseHash(encodeur.encode(compte.motDePasse()));
        utilisateur.setActif(true);
        UUID identifiant = depotUtilisateur.save(utilisateur).getId();

        Membre membre = new Membre();
        membre.setOrganisationId(organisation);
        membre.setUtilisateurId(identifiant);
        membre.setRole(Membre.Role.ADMIN_ENTREPRISE);
        depotMembre.save(membre);
    }

    private UUID creerAppelOffres(UUID organisation, DossierReference.AppelOffres source) {
        AppelOffres appel = new AppelOffres();
        appel.setOrganisationId(organisation);
        appel.setNumeroAo(source.numeroAo());
        appel.setIntitule(source.intitule());
        appel.setAutoriteContractante(source.autoriteContractante());
        appel.setMaitreOuvrage(source.maitreOuvrage());
        appel.setDestinataire(source.destinataire());
        appel.setFinancement(source.financement());
        appel.setValiditeOffreJours(source.validiteOffreJours());
        appel.setDateLimiteDepot(instant(source.dateLimiteDepot()));
        appel.setStatut(AppelOffres.Statut.A_VERIFIER);
        // Ce que le dossier de reference ne precise pas reste non precise, et
        // l'avertissement correspondant est la question a trancher.
        appel.setErreurAnalyse(source.erreurAnalyse());
        return depotAppelOffres.save(appel).getId();
    }

    private void creerLots(UUID organisation, UUID appelOffres, List<DossierReference.Lot> lots) {
        if (lots == null) {
            return;
        }
        int rang = 0;
        for (DossierReference.Lot source : lots) {
            rang++;
            Lot lot = new Lot();
            lot.setOrganisationId(organisation);
            lot.setAppelOffresId(appelOffres);
            lot.setNumero(source.numero() == null ? rang : source.numero());
            lot.setIntitule(source.intitule());
            lot.setLocalite(source.localite());
            lot.setDelaiMois(source.delaiMois());
            lot.setMontantGarantie(source.montantGarantie());
            depotLot.save(lot);
        }
    }

    /**
     * Les exigences du dossier d'appel d'offres. Ce sont des exigences, pas
     * des moyens : le personnel propose et le parc reel de l'entreprise
     * restent a saisir, et leur absence fera echouer les controles bloquants.
     */
    private void creerExigences(UUID organisation, UUID appelOffres,
                                DossierReference reference) {
        if (reference.exigences() == null) {
            return;
        }
        String source = reference.appelOffres().sourceDao();
        for (DossierReference.Exigence attendue : reference.exigences()) {
            Exigence exigence = new Exigence();
            exigence.setOrganisationId(organisation);
            exigence.setAppelOffresId(appelOffres);
            exigence.setType(Exigence.Type.valueOf(attendue.type()));
            exigence.setLibelle(attendue.libelle());
            exigence.setValeur(attendue.valeur());
            exigence.setQuantiteMin(attendue.quantiteMin());
            exigence.setAnneesMin(attendue.anneesMin());
            exigence.setEliminatoire(true);
            exigence.setSourceDao(source);
            depotExigence.save(exigence);
        }
    }

    // ------------------------------------------------------------------ interne

    private java.time.Instant instant(String horodatage) {
        if (horodatage == null || horodatage.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(horodatage).atZone(ABIDJAN).toInstant();
        } catch (DateTimeParseException erreur) {
            LOG.warn("Date limite de depot illisible ({}), attendue au format "
                    + "2026-08-14T23:59. Elle reste vide, et le controle le dira.",
                    horodatage);
            return null;
        }
    }

    /**
     * Le mot de passe n'est jamais journalise : il est dans le fichier de
     * celui qui a pose le reglage, et les journaux se recopient, se centralisent
     * et se lisent a plusieurs.
     */
    private void annoncer(DossierReference reference) {
        LOG.warn("""

                ========================================================
                 DOSSIER DE RÉFÉRENCE CHARGÉ — {}
                ========================================================
                 Connexion : {}
                 Mot de passe : celui de compte.motDePasse dans {}
                 Rôle      : administrateur d'entreprise

                 Le coffre, le personnel proposé et le parc matériel
                 sont tels que le fichier les laisse. Rien n'a été
                 inventé pour les combler : les contrôles bloquants
                 vont donc signaler ce qui manque — c'est exactement
                 ce qu'ils doivent faire.

                 La validation demandera un second compte : celui qui
                 a préparé un dossier ne peut pas le valider.
                ========================================================
                """,
                reference.appelOffres().numeroAo(),
                reference.compte().courriel(),
                fichier.toAbsolutePath());
    }
}
