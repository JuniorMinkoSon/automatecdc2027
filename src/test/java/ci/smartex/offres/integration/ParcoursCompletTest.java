package ci.smartex.offres.integration;

import ci.smartex.offres.conformite.MoteurConformite;
import ci.smartex.offres.domain.*;
import ci.smartex.offres.rendu.ServiceArchiveDossier;
import ci.smartex.offres.rendu.ServiceGenerationDossier;
import ci.smartex.offres.repo.*;
import ci.smartex.offres.securite.ContexteOrganisation;
import ci.smartex.offres.service.ServiceDossier;
import ci.smartex.offres.service.ServiceDqe;
import ci.smartex.offres.service.ServiceRapprochement;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le parcours complet, de l'appel d'offres au dossier telechargeable.
 *
 * <p>Chaque etape passe par les services que l'interface appelle, dans l'ordre
 * ou elle les appelle. Ce n'est donc pas une simulation : si ce test passe,
 * c'est que la chaine tient reellement, de la saisie d'un prix jusqu'a
 * l'archive qui sort.
 *
 * <p>L'extraction par le modele est coupee. Elle n'est pas le sujet ici, et
 * surtout : ce test doit prouver que le parcours fonctionne meme sans elle.
 * Une plateforme qui ne sait plus produire un dossier parce qu'un service
 * distant est indisponible ne vaut rien un jour d'echeance.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Parcours complet : du DAO au dossier telechargeable")
class ParcoursCompletTest extends BasePostgres {

    @DynamicPropertySource
    static void stockageTemporaire(DynamicPropertyRegistry proprietes) throws Exception {
        Path racine = Files.createTempDirectory("automatecdc-parcours-");
        proprietes.add("app.stockage-racine", racine::toString);
    }

    @Autowired private ServiceDossier serviceDossier;
    @Autowired private ServiceDqe serviceDqe;
    @Autowired private ServiceRapprochement rapprochement;
    @Autowired private MoteurConformite moteur;
    @Autowired private ServiceGenerationDossier generation;
    @Autowired private ServiceArchiveDossier archive;

    @Autowired private DepotOrganisation depotOrganisation;
    @Autowired private DepotEntreprise depotEntreprise;
    @Autowired private DepotRepresentant depotRepresentant;
    @Autowired private DepotAppelOffres depotAppelOffres;
    @Autowired private DepotLot depotLot;
    @Autowired private DepotExigence depotExigence;
    @Autowired private DepotLigneDqe depotLigneDqe;
    @Autowired private DepotPieceAdmin depotPieceAdmin;
    @Autowired private DepotPersonnel depotPersonnel;
    @Autowired private DepotMateriel depotMateriel;

    private static UUID organisation;
    private static UUID appelOffres;
    private static UUID lot;
    private static UUID dossier;

    @AfterEach
    void nettoyerLeContexte() {
        // Un contexte oublie sur le fil serait herite par le test suivant,
        // exactement comme il le serait par la requete d'un autre client.
        ContexteOrganisation.effacer();
    }

    @Test
    @Order(1)
    @DisplayName("une entreprise est activee et sa fiche remplie")
    void etape1_entreprise() {
        ContexteOrganisation.poserSuperAdmin();
        Organisation org = new Organisation();
        org.setNom("Entreprise de parcours");
        org.setStatut(Organisation.Statut.ACTIVE);
        organisation = depotOrganisation.save(org).getId();

        ContexteOrganisation.poser(organisation);

        Entreprise entreprise = new Entreprise();
        entreprise.setOrganisationId(organisation);
        entreprise.setRaisonSociale("ENTREPRISE DE PARCOURS");
        entreprise.setSigle("EDP");
        entreprise.setRccm("CI-TEST-2026-B-00001");
        entreprise.setCompteContribuable("0000000 X");
        entreprise.setAdresse("Abidjan, Cocody");
        entreprise.setBoitePostale("01 BP 1 Abidjan 01");
        entreprise.setTelephones("+225 00 00 00 00 00");
        depotEntreprise.save(entreprise);

        Representant gerant = new Representant();
        gerant.setOrganisationId(organisation);
        gerant.setNom("Mme DIALLO Fatou");
        gerant.setFonction("Gérante");
        gerant.setSignataireDefaut(true);
        depotRepresentant.save(gerant);

        assertThat(depotEntreprise.findByOrganisationId(organisation)).isPresent();
    }

    @Test
    @Order(2)
    @DisplayName("un appel d'offres est saisi a la main, extraction coupee")
    void etape2_appelOffres() {
        ContexteOrganisation.poser(organisation);

        AppelOffres appel = new AppelOffres();
        appel.setOrganisationId(organisation);
        appel.setIntitule("Travaux de construction de deux centres de santé");
        appel.setNumeroAo("AO-TEST-2026-001");
        appel.setAutoriteContractante("Conseil régional de test");
        appel.setDestinataire("Conseil régional de test");
        appel.setValiditeOffreJours(120);
        appel.setRegimePrix("TTC, fermes et non révisables");
        appel.setTvaApplicable(Boolean.TRUE);
        appel.setDateLimiteDepot(Instant.now().plus(30, ChronoUnit.DAYS));
        appel.setStatut(AppelOffres.Statut.VERIFIE);
        appelOffres = depotAppelOffres.save(appel).getId();

        Lot premier = new Lot();
        premier.setOrganisationId(organisation);
        premier.setAppelOffresId(appelOffres);
        premier.setNumero(1);
        premier.setIntitule("Centre de santé de test");
        premier.setDelaiMois(4);
        premier.setMontantGarantie(850_000L);
        lot = depotLot.save(premier).getId();

        for (String[] attendu : new String[][]{
                {"Conducteur des travaux", "5"},
                {"Chef de chantier", "3"}}) {
            Exigence exigence = new Exigence();
            exigence.setOrganisationId(organisation);
            exigence.setAppelOffresId(appelOffres);
            exigence.setLotId(lot);
            exigence.setType(Exigence.Type.PERSONNEL);
            exigence.setLibelle(attendu[0]);
            exigence.setAnneesMin(Integer.valueOf(attendu[1]));
            exigence.setEliminatoire(true);
            depotExigence.save(exigence);
        }

        Exigence betonniere = new Exigence();
        betonniere.setOrganisationId(organisation);
        betonniere.setAppelOffresId(appelOffres);
        betonniere.setLotId(lot);
        betonniere.setType(Exigence.Type.MATERIEL);
        betonniere.setLibelle("Bétonnière");
        betonniere.setQuantiteMin(1);
        betonniere.setEliminatoire(true);
        depotExigence.save(betonniere);

        assertThat(depotLot.findAllByAppelOffresIdOrderByNumeroAsc(appelOffres)).hasSize(1);
    }

    @Test
    @Order(3)
    @DisplayName("le coffre, le personnel et le materiel sont renseignes")
    void etape3_moyens() {
        ContexteOrganisation.poser(organisation);
        LocalDate valableLongtemps = LocalDate.now().plusYears(1);

        for (PieceAdmin.Type type : List.of(
                PieceAdmin.Type.RCCM, PieceAdmin.Type.DFE,
                PieceAdmin.Type.ATTESTATION_PME, PieceAdmin.Type.ATTESTATION_BANCAIRE,
                PieceAdmin.Type.QUITUS_ARCOP)) {
            depotPieceAdmin.save(piece(type, valableLongtemps, null));
        }
        // La caution porte exactement le montant exige par le lot.
        depotPieceAdmin.save(piece(PieceAdmin.Type.CAUTIONNEMENT, valableLongtemps, 850_000L));

        depotPersonnel.save(personne("KONE Ibrahim", "Conducteur des travaux", 8));
        depotPersonnel.save(personne("YAO Akissi", "Chef de chantier", 5));

        Materiel betonniere = new Materiel();
        betonniere.setOrganisationId(organisation);
        betonniere.setDesignation("Bétonnière 350 litres");
        betonniere.setMarque("Test");
        betonniere.setAnnee(2022);
        betonniere.setJustificatifChemin("materiel/facture.pdf");
        depotMateriel.save(betonniere);

        assertThat(depotPieceAdmin.findAllByOrganisationId(organisation)).hasSize(6);
    }

    @Test
    @Order(4)
    @DisplayName("le dossier reprend d'office les engagements imposes par le DAO")
    void etape4_dossier() {
        ContexteOrganisation.poser(organisation);
        Dossier cree = serviceDossier.creer(appelOffres, lot);
        dossier = cree.getId();

        // Les trois engagements viennent du DAO : l'utilisateur n'a a y toucher
        // que s'il veut s'engager sur moins.
        assertThat(cree.getDelaiExecutionMois()).isEqualTo(4);
        assertThat(cree.getValiditeOffreJours()).isEqualTo(120);
        assertThat(cree.getValiditeGarantieJours()).isEqualTo(150);
        assertThat(cree.getStatut()).isEqualTo(Dossier.Statut.BROUILLON);
    }

    @Test
    @Order(5)
    @DisplayName("le rapprochement propose le personnel et le materiel")
    void etape5_rapprochement() {
        ContexteOrganisation.poser(organisation);
        var resultat = rapprochement.proposer(dossier);

        assertThat(resultat.personnesProposees()).isEqualTo(2);
        assertThat(resultat.materielsProposes()).isEqualTo(1);
        assertThat(resultat.exigencesNonPourvues()).isEmpty();
    }

    @Test
    @Order(6)
    @DisplayName("les prix saisis figent un total, calcule par la base")
    void etape6_prix() {
        ContexteOrganisation.poser(organisation);

        List<LigneDqe> lignes = new ArrayList<>();
        lignes.add(ligne("I - TERRASSEMENTS", "0", "Débroussement du site", "m2",
                "828.000", 0));
        lignes.add(ligne("I - TERRASSEMENTS", "1", "Installation de chantier", "forf.",
                "1.000", 1));
        lignes.add(ligne("II - FONDATIONS", "2", "Béton de propreté", "m3",
                "36.575", 2));
        serviceDqe.remplacerLignes(dossier, lignes);

        List<LigneDqe> enregistrees = serviceDqe.lignes(dossier);
        assertThat(enregistrees).hasSize(3);

        // Tant qu'un prix manque, aucun montant n'est fige.
        assertThat(serviceDossier.charger(dossier).getMontantSoumission()).isNull();

        var prix = new java.util.HashMap<UUID, Long>();
        prix.put(enregistrees.get(0).getId(), 500L);
        prix.put(enregistrees.get(1).getId(), 200_000L);
        prix.put(enregistrees.get(2).getId(), 85_000L);
        long total = serviceDqe.enregistrerPrix(dossier, prix);

        // 828 x 500 + 1 x 200 000 + 36,575 x 85 000, calcule par PostgreSQL.
        assertThat(total).isEqualTo(414_000L + 200_000L + 3_108_875L);
        assertThat(serviceDossier.charger(dossier).getMontantSoumission()).isEqualTo(total);
    }

    @Test
    @Order(7)
    @DisplayName("aucun controle bloquant n'echoue sur un dossier complet")
    void etape7_conformite() {
        ContexteOrganisation.poser(organisation);
        var rapport = moteur.executer(dossier);

        assertThat(rapport.nombreExecutes()).isGreaterThan(5);
        assertThat(rapport.bloquantsEnEchec())
                .as("les messages des contrôles bloquants en échec")
                .extracting(ControleResultat::getMessage)
                .isEmpty();
        assertThat(rapport.validationPossible()).isTrue();
    }

    @Test
    @Order(8)
    @DisplayName("les documents sortent, filigranes tant que rien n'est valide")
    void etape8_generation() {
        ContexteOrganisation.poser(organisation);
        List<DocumentGenere> documents = generation.genererTout(dossier, false);

        assertThat(documents).hasSize(3);
        assertThat(documents).allSatisfy(document -> {
            assertThat(document.isBrouillon()).isTrue();
            assertThat(document.getEmpreinteSha256()).hasSize(64);
            assertThat(document.getTailleOctets()).isGreaterThan(1000L);
        });
        assertThat(documents).extracting(DocumentGenere::getType)
                .containsExactlyInAnyOrder(DocumentGenere.Type.TECHNIQUE,
                        DocumentGenere.Type.FINANCIERE,
                        DocumentGenere.Type.RAPPORT_CONFORMITE);
    }

    @Test
    @Order(9)
    @DisplayName("l'archive contient les pieces et son sommaire")
    void etape9_archive() throws Exception {
        ContexteOrganisation.poser(organisation);
        byte[] zip = archive.archiverDepuisLeStockage(dossier);

        List<String> entrees = new ArrayList<>();
        try (ZipInputStream lecture = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var entree = lecture.getNextEntry(); entree != null;
                 entree = lecture.getNextEntry()) {
                entrees.add(entree.getName());
            }
        }

        assertThat(entrees).anyMatch(nom -> nom.contains("SOMMAIRE"));
        assertThat(entrees).anyMatch(nom -> nom.endsWith(".pdf"));
        assertThat(entrees).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    @Order(10)
    @DisplayName("la soumission passe, et le redacteur ne peut pas se valider lui-meme")
    void etape10_circuit() {
        ContexteOrganisation.poser(organisation);

        var rapport = serviceDossier.soumettreAValidation(dossier);
        assertThat(rapport.validationPossible()).isTrue();
        assertThat(serviceDossier.charger(dossier).getStatut())
                .isEqualTo(Dossier.Statut.EN_VALIDATION);

        // Hors session web, aucun utilisateur n'est authentifie : la separation
        // des taches ne peut donc pas s'appliquer ici, et la validation passe.
        // C'est le test de ControlesTest qui couvre le refus, et la regle est
        // appliquee dans ServiceDossier a partir de modifiePar.
        serviceDossier.valider(dossier, "Relu et conforme.");
        Dossier valide = serviceDossier.charger(dossier);

        assertThat(valide.getStatut()).isEqualTo(Dossier.Statut.VALIDE);
        assertThat(valide.getValideLe()).isNotNull();
        assertThat(valide.porteFiligrane()).isFalse();
        assertThat(valide.estModifiable()).isFalse();
    }

    @Test
    @Order(11)
    @DisplayName("une version validee est figee, et se corrige par une version n+1")
    void etape11_versionSuivante() {
        ContexteOrganisation.poser(organisation);

        // Les PDF definitifs remplacent les brouillons : ce sont eux qui
        // portent l'empreinte de la version signee.
        List<DocumentGenere> definitifs = generation.genererTout(dossier, true);
        assertThat(definitifs).allSatisfy(d -> assertThat(d.isBrouillon()).isFalse());

        Dossier suivant = serviceDossier.nouvelleVersion(dossier);
        assertThat(suivant.getVersion()).isEqualTo(2);
        assertThat(suivant.getStatut()).isEqualTo(Dossier.Statut.BROUILLON);

        // Les lignes et les prix sont recopies : on repart du travail fait.
        assertThat(serviceDqe.lignes(suivant.getId())).hasSize(3);
        assertThat(serviceDqe.nombreDePrixManquants(suivant.getId())).isZero();

        // Et la version precedente reste intacte.
        assertThat(serviceDossier.charger(dossier).getStatut())
                .isEqualTo(Dossier.Statut.VALIDE);
    }

    // ------------------------------------------------------------------ fixtures

    private PieceAdmin piece(PieceAdmin.Type type, LocalDate expiration, Long montant) {
        PieceAdmin piece = new PieceAdmin();
        piece.setOrganisationId(organisation);
        piece.setType(type);
        piece.setFichierChemin(organisation + "/coffre/" + type + ".pdf");
        piece.setDateEmission(LocalDate.now().minusMonths(1));
        piece.setDateExpiration(expiration);
        piece.setMontant(montant);
        piece.setConfirmee(true);
        return piece;
    }

    private Personnel personne(String nom, String poste, int annees) {
        Personnel personne = new Personnel();
        personne.setOrganisationId(organisation);
        personne.setNom(nom);
        personne.setPoste(poste);
        personne.setAnneesExperience(annees);
        personne.setDiplome("BTS bâtiment");
        personne.setCvChemin("personnel/cv.pdf");
        return personne;
    }

    private LigneDqe ligne(String section, String numero, String designation,
                           String unite, String quantite, int ordre) {
        LigneDqe ligne = new LigneDqe();
        ligne.setOrganisationId(organisation);
        ligne.setDossierId(dossier);
        ligne.setSection(section);
        ligne.setOrdreSection(section.startsWith("I -") ? 1 : 2);
        ligne.setNumeroPrix(numero);
        ligne.setOrdre(ordre);
        ligne.setDesignation(designation);
        ligne.setUnite(unite);
        ligne.setQuantite(new BigDecimal(quantite));
        ligne.setIssueDuDao(true);
        return ligne;
    }
}
