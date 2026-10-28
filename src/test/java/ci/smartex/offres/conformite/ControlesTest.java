package ci.smartex.offres.conformite;

import ci.smartex.offres.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les controles sont des fonctions pures sur un etat fourni : ils se testent
 * donc sans base, sans Spring et sans fichier. Les cas ci-dessous sont ceux
 * releves dans le dossier de reference et dans son DAO.
 */
@DisplayName("Controles de conformite")
class ControlesTest {

    private static final UUID ORGANISATION = UUID.randomUUID();
    private static final LocalDate DATE_LIMITE = LocalDate.of(2026, 9, 30);

    @Nested
    @DisplayName("Delais")
    class Delais {

        @Test
        @DisplayName("un delai engage plus long que celui du DAO bloque")
        void delaiTropLong() {
            var dossier = dossier();
            dossier.setDelaiExecutionMois(6);

            var verdict = new ControlesDelais.DelaiExecution()
                    .verifier(contexte(dossier, lotDeQuatreMois(), List.of()));

            assertThat(verdict.reussi()).isFalse();
            assertThat(verdict.message()).contains("6 mois").contains("4 mois");
        }

        @Test
        @DisplayName("un delai egal au maximum passe")
        void delaiEgal() {
            var dossier = dossier();
            dossier.setDelaiExecutionMois(4);

            assertThat(new ControlesDelais.DelaiExecution()
                    .verifier(contexte(dossier, lotDeQuatreMois(), List.of())).reussi())
                    .isTrue();
        }

        @Test
        @DisplayName("une validite plus longue que demandee est refusee aussi")
        void validitePlusLongue() {
            var dossier = dossier();
            dossier.setValiditeOffreJours(150);

            var verdict = new ControlesDelais.ValiditeOffre()
                    .verifier(contexte(dossier, lotDeQuatreMois(), List.of()));

            assertThat(verdict.reussi()).isFalse();
            assertThat(verdict.message()).contains("150").contains("120");
        }

        @Test
        @DisplayName("la garantie doit valoir la validite de l'offre plus trente jours")
        void validiteGarantie() {
            var dossier = dossier();
            dossier.setValiditeGarantieJours(150);
            assertThat(new ControlesDelais.ValiditeGarantie()
                    .verifier(contexte(dossier, lotDeQuatreMois(), List.of())).reussi())
                    .isTrue();

            dossier.setValiditeGarantieJours(120);
            assertThat(new ControlesDelais.ValiditeGarantie()
                    .verifier(contexte(dossier, lotDeQuatreMois(), List.of())).reussi())
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("Pieces du coffre")
    class Pieces {

        @Test
        @DisplayName("un quitus qui expire avant la date limite bloque, meme valable aujourd'hui")
        void quitusExpireAvantLeDepot() {
            var quitus = piece(PieceAdmin.Type.QUITUS_ARCOP, DATE_LIMITE.minusDays(1));

            var verdict = new ControlesPieces.QuitusArcop()
                    .verifier(contexte(dossier(), lotDeQuatreMois(), List.of(quitus)));

            assertThat(verdict.reussi()).isFalse();
            assertThat(verdict.message()).contains("avant la date limite");
        }

        @Test
        @DisplayName("un quitus valable le jour du depot passe")
        void quitusValableLeJourJ() {
            var quitus = piece(PieceAdmin.Type.QUITUS_ARCOP, DATE_LIMITE);

            assertThat(new ControlesPieces.QuitusArcop()
                    .verifier(contexte(dossier(), lotDeQuatreMois(), List.of(quitus))).reussi())
                    .isTrue();
        }

        @Test
        @DisplayName("une piece non confirmee ne compte pas")
        void pieceNonConfirmee() {
            var quitus = piece(PieceAdmin.Type.QUITUS_ARCOP, DATE_LIMITE.plusYears(1));
            quitus.setConfirmee(false);

            var verdict = new ControlesPieces.QuitusArcop()
                    .verifier(contexte(dossier(), lotDeQuatreMois(), List.of(quitus)));

            assertThat(verdict.reussi()).isFalse();
            assertThat(verdict.message()).contains("confirmation");
        }

        @Test
        @DisplayName("une caution inferieure au montant exige bloque")
        void cautionInsuffisante() {
            var caution = piece(PieceAdmin.Type.CAUTIONNEMENT, DATE_LIMITE.plusYears(1));
            caution.setMontant(500_000L);

            var verdict = new ControlesPieces.GarantieOffre()
                    .verifier(contexte(dossier(), lotDeQuatreMois(), List.of(caution)));

            assertThat(verdict.reussi()).isFalse();
            assertThat(verdict.message()).contains("850 000 F CFA");
        }

        @Test
        @DisplayName("une caution dont le montant n'a pas ete lu ne bloque pas")
        void cautionIllisible() {
            var caution = piece(PieceAdmin.Type.CAUTIONNEMENT, DATE_LIMITE.plusYears(1));
            caution.setMontant(null);

            assertThat(new ControlesPieces.GarantieOffre()
                    .verifier(contexte(dossier(), lotDeQuatreMois(), List.of(caution))).reussi())
                    .isTrue();
            assertThat(new ControlesPieces.MontantGarantieARelire()
                    .sApplique(contexte(dossier(), lotDeQuatreMois(), List.of(caution))))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Chiffrage")
    class Chiffrage {

        @Test
        @DisplayName("un DQE dont un prix manque bloque")
        void prixManquant() {
            var contexte = contexteAvecLignes(
                    ligne("I", "0", "828.000", 500L),
                    ligne("I", "1", "1.000", null));

            var verdict = new ControlesChiffrage.ArithmetiqueDqe().verifier(contexte);

            assertThat(verdict.reussi()).isFalse();
            assertThat(verdict.message()).contains("1 prix unitaire");
        }

        @Test
        @DisplayName("le total du lot 1 du dossier de reference se recompose exactement")
        void totalDuLotUn() {
            var contexte = contexteAvecLignes(
                    ligne("I", "0", "1.000", 16_214_800L),
                    ligne("II", "1", "1.000", 1_096_664L),
                    ligne("III", "2", "1.000", 1_328_201L));

            assertThat(contexte.totalDqe()).isEqualTo(18_639_665L);
            assertThat(new ControlesChiffrage.ArithmetiqueDqe().verifier(contexte).reussi())
                    .isTrue();
            assertThat(new ControlesChiffrage.NumerosDePrixUniques().verifier(contexte).reussi())
                    .isTrue();
        }

        @Test
        @DisplayName("une lettre de soumission perimee est signalee")
        void montantFigeDepasse() {
            var contexte = contexteAvecLignes(ligne("I", "0", "1.000", 18_639_665L));
            contexte.dossier().setMontantSoumission(17_000_000L);

            var verdict = new ControlesChiffrage.MontantEnLettresCoherent().verifier(contexte);

            assertThat(verdict.reussi()).isFalse();
            assertThat(verdict.message()).contains("Regenerez le brouillon");
        }

        @Test
        @DisplayName("deux lots au franc pres font avertir, comme les lots 1 et 2 deposes")
        void lotsAuMemeMontant() {
            var contexte = contexteAvecLignes(ligne("I", "0", "1.000", 18_639_665L));
            contexte.dossier().setMontantSoumission(18_639_665L);

            var avec = new DossierAControler(
                    contexte.dossier(), contexte.appelOffres(), contexte.lot(),
                    contexte.entreprise(), contexte.exigences(), contexte.lignes(),
                    contexte.pieces(), contexte.personnel(), contexte.materiel(),
                    contexte.affectationsPersonnel(), contexte.affectationsMateriel(),
                    contexte.autresLotsDuMemeAppel(), contexte.affectationsPersonnelAutresLots(),
                    List.of(18_639_665L), contexte.dateReference());

            var controle = new ControlesChiffrage.LotsAuMemeMontant();
            assertThat(controle.sApplique(avec)).isTrue();
            assertThat(controle.verifier(avec).reussi()).isFalse();
        }
    }

    @Nested
    @DisplayName("Personnel")
    class PersonnelCle {

        @Test
        @DisplayName("un poste exige sans personne affectee bloque, comme le PER-1 laisse vide")
        void posteNonPourvu() {
            var contexte = contexteAvecPersonnel(List.of(), List.of());

            var verdict = new ControlesMoyens.PersonnelCle().verifier(contexte);

            assertThat(verdict.reussi()).isFalse();
            assertThat(verdict.message()).contains("Chef de chantier manquant");
        }

        @Test
        @DisplayName("une experience insuffisante bloque")
        void experienceInsuffisante() {
            var chef = personne("KOUASSI Yao", "Chef de chantier", 2);
            var contexte = contexteAvecPersonnel(List.of(chef),
                    List.of(affectation(chef.getId(), "Chef de chantier")));

            var verdict = new ControlesMoyens.PersonnelCle().verifier(contexte);

            assertThat(verdict.reussi()).isFalse();
            assertThat(verdict.message()).contains("2 ans").contains("3");
        }

        @Test
        @DisplayName("la meme personne sur deux lots du meme appel bloque")
        void personneEngageeSurDeuxLots() {
            var chef = personne("KOUASSI Yao", "Chef de chantier", 5);
            var surCeLot = affectation(chef.getId(), "Chef de chantier");
            var surLAutreLot = affectation(chef.getId(), "Chef de chantier");

            var base = contexteAvecPersonnel(List.of(chef), List.of(surCeLot));
            var contexte = new DossierAControler(
                    base.dossier(), base.appelOffres(), base.lot(), base.entreprise(),
                    base.exigences(), base.lignes(), base.pieces(), base.personnel(),
                    base.materiel(), base.affectationsPersonnel(), base.affectationsMateriel(),
                    base.autresLotsDuMemeAppel(), List.of(surLAutreLot),
                    base.totauxDesAutresLots(), base.dateReference());

            var verdict = new ControlesMoyens.PersonnelCle().verifier(contexte);

            assertThat(verdict.reussi()).isFalse();
            assertThat(verdict.message()).contains("deja engage sur un autre lot");
        }
    }

    // ------------------------------------------------------------------ fixtures

    private static Dossier dossier() {
        var dossier = new Dossier();
        dossier.setId(UUID.randomUUID());
        dossier.setOrganisationId(ORGANISATION);
        dossier.setAppelOffresId(UUID.randomUUID());
        dossier.setLotId(UUID.randomUUID());
        dossier.setDelaiExecutionMois(4);
        dossier.setValiditeOffreJours(120);
        dossier.setValiditeGarantieJours(150);
        return dossier;
    }

    private static AppelOffres appelOffres() {
        var ao = new AppelOffres();
        ao.setId(UUID.randomUUID());
        ao.setOrganisationId(ORGANISATION);
        ao.setNumeroAo("AO-REFERENCE-001");
        ao.setIntitule("Construction de centres de sante");
        ao.setAutoriteContractante("Conseil Regional");
        ao.setValiditeOffreJours(120);
        ao.setRegimePrix("TTC, fermes et non revisables");
        return ao;
    }

    private static Optional<Lot> lotDeQuatreMois() {
        var lot = new Lot();
        lot.setId(UUID.randomUUID());
        lot.setOrganisationId(ORGANISATION);
        lot.setNumero(1);
        lot.setIntitule("Premiere localite");
        lot.setDelaiMois(4);
        lot.setMontantGarantie(850_000L);
        return Optional.of(lot);
    }

    private static Entreprise entreprise() {
        var entreprise = new Entreprise();
        entreprise.setId(UUID.randomUUID());
        entreprise.setOrganisationId(ORGANISATION);
        entreprise.setRaisonSociale("ENTREPRISE DE REFERENCE");
        entreprise.setSigle("EDR");
        entreprise.setRccm("CI-ABJ-2019-B-12345");
        entreprise.setCompteContribuable("1945678 K");
        return entreprise;
    }

    private static PieceAdmin piece(PieceAdmin.Type type, LocalDate expiration) {
        var piece = new PieceAdmin();
        piece.setId(UUID.randomUUID());
        piece.setOrganisationId(ORGANISATION);
        piece.setType(type);
        piece.setFichierChemin("coffre/" + type + ".pdf");
        piece.setDateEmission(LocalDate.of(2026, 1, 1));
        piece.setDateExpiration(expiration);
        piece.setConfirmee(true);
        return piece;
    }

    private static Personnel personne(String nom, String poste, int annees) {
        var personne = new Personnel();
        personne.setId(UUID.randomUUID());
        personne.setOrganisationId(ORGANISATION);
        personne.setNom(nom);
        personne.setPoste(poste);
        personne.setAnneesExperience(annees);
        personne.setCvChemin("personnel/cv.pdf");
        return personne;
    }

    private static AffectationPersonnel affectation(UUID personnelId, String poste) {
        var affectation = new AffectationPersonnel();
        affectation.setId(UUID.randomUUID());
        affectation.setOrganisationId(ORGANISATION);
        affectation.setPersonnelId(personnelId);
        affectation.setPosteExige(poste);
        return affectation;
    }

    private static Exigence exigencePersonnel(String libelle, int anneesMin) {
        var exigence = new Exigence();
        exigence.setId(UUID.randomUUID());
        exigence.setOrganisationId(ORGANISATION);
        exigence.setType(Exigence.Type.PERSONNEL);
        exigence.setLibelle(libelle);
        exigence.setAnneesMin(anneesMin);
        exigence.setEliminatoire(true);
        return exigence;
    }

    private static LigneDqe ligne(String section, String numero, String quantite, Long prix) {
        var ligne = new LigneDqe();
        ligne.setId(UUID.randomUUID());
        ligne.setOrganisationId(ORGANISATION);
        ligne.setSection(section);
        ligne.setNumeroPrix(numero);
        ligne.setDesignation("Prestation " + numero);
        ligne.setUnite("m3");
        ligne.setQuantite(new BigDecimal(quantite));
        ligne.setPrixUnitaire(prix);
        // La colonne generee est calculee par PostgreSQL ; hors base, on la
        // reproduit a l'identique pour que le test porte sur la meme valeur.
        if (prix != null) {
            MontantDeTest.poser(ligne, new BigDecimal(quantite)
                    .multiply(BigDecimal.valueOf(prix))
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .longValueExact());
        }
        return ligne;
    }

    private static DossierAControler contexte(Dossier dossier,
                                              Optional<Lot> lot,
                                              List<PieceAdmin> pieces) {
        return new DossierAControler(dossier, appelOffres(), lot, entreprise(),
                List.of(), List.of(), pieces, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), DATE_LIMITE);
    }

    private static DossierAControler contexteAvecLignes(LigneDqe... lignes) {
        return new DossierAControler(dossier(), appelOffres(), lotDeQuatreMois(), entreprise(),
                List.of(), List.of(lignes), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), DATE_LIMITE);
    }

    private static DossierAControler contexteAvecPersonnel(List<Personnel> personnel,
                                                           List<AffectationPersonnel> affectations) {
        List<Exigence> exigences = new ArrayList<>();
        exigences.add(exigencePersonnel("Conducteur des travaux", 5));
        exigences.add(exigencePersonnel("Chef de chantier", 3));

        return new DossierAControler(dossier(), appelOffres(), lotDeQuatreMois(), entreprise(),
                exigences, List.of(), List.of(), personnel, List.of(),
                affectations, List.of(), List.of(), List.of(), List.of(), DATE_LIMITE);
    }
}
