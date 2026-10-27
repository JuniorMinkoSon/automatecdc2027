package ci.smartex.offres.rendu;

import ci.smartex.offres.domain.AppelOffres;
import ci.smartex.offres.domain.Lot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'avis d'appel d'offres : le document que les candidats lisent avant tout
 * autre, et sur lequel ils règlent leur offre.
 */
@DisplayName("Avis d'appel d'offres")
class AvisAppelOffresTest {

    private final ServiceJasper jasper = new ServiceJasper();
    private final AssembleurPdf assembleur = new AssembleurPdf();

    @Test
    @DisplayName("le cautionnement s'écrit en lettres et en chiffres, du même nombre")
    void cautionnementSansEcartPossible() {
        String clauses = TextesAvis.clauses(appel(), List.of(lot(1, 850_000L), lot(2, 850_000L)));

        // C'est la réponse à « cinq cent cinquante mille (850 000) » : les deux
        // écritures sortent du même entier, par la même fonction.
        assertThat(clauses)
                .contains("huit cent cinquante mille (850 000 F CFA) par lot")
                .doesNotContain("cinq cent cinquante mille");
    }

    @Test
    @DisplayName("des garanties différentes par lot sont détaillées lot par lot")
    void garantiesDifferentes() {
        String clauses = TextesAvis.clauses(appel(), List.of(lot(1, 850_000L), lot(2, 1_200_000L)));

        assertThat(clauses)
                .contains("varie selon le lot")
                .contains("lot 1 : huit cent cinquante mille")
                .contains("lot 2 : un million deux cent mille");
    }

    @Test
    @DisplayName("la validité n'est lue qu'une fois, donc ne peut pas se contredire")
    void validiteUnique() {
        AppelOffres appel = appel();
        appel.setValiditeAnnonceeJours(120);
        appel.setValiditeOffreJours(120);

        String clauses = TextesAvis.clauses(appel, List.of(lot(1, 850_000L)));

        assertThat(clauses).contains("cent vingt (120) jours");
        assertThat(clauses.split("jours à compter de la date limite")).hasSize(2);
    }

    @Test
    @DisplayName("un champ absent se signale au lieu de laisser un blanc")
    void champAbsentVisible() {
        AppelOffres incomplet = new AppelOffres();
        incomplet.setIntitule("Travaux de construction");

        String clauses = TextesAvis.clauses(incomplet, List.of());

        assertThat(clauses)
                .contains("[à compléter : l'autorité contractante]")
                .contains("[À COMPLÉTER : aucun lot n'est défini")
                .contains("[À COMPLÉTER : la date limite de dépôt n'est pas fixée");
    }

    @Test
    @DisplayName("l'avis se rend en PDF avec son tableau de lots")
    void renduPdf() {
        AppelOffres appel = appel();
        List<Lot> lots = List.of(lot(1, 850_000L), lot(2, 850_000L));

        Map<String, Object> parametres = new HashMap<>();
        parametres.put("MINISTERE", "MINISTÈRE DE L'INTÉRIEUR ET DE LA SÉCURITÉ");
        parametres.put("REGION", "RÉGION DE TEST");
        parametres.put("AUTORITE", appel.getAutoriteContractante());
        parametres.put("NUMERO_AVIS", appel.getNumeroAo());
        parametres.put("OBJET", appel.getIntitule());
        parametres.put("FINANCEMENT", appel.getFinancement());
        parametres.put("LIGNE_BUDGETAIRE", appel.getLigneBudgetaire());
        parametres.put("CLAUSES", TextesAvis.clauses(appel, lots));

        List<LigneLot> lignes = lots.stream()
                .map(lot -> new LigneLot(String.valueOf(lot.getNumero()),
                        lot.getIntitule(),
                        lot.getDelaiMois() + " mois"))
                .toList();

        byte[] pdf = jasper.versPdf("rapports/avis_appel_offres.jrxml", parametres, lignes);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).startsWith("%PDF-");
        assertThat(assembleur.nombreDePages(pdf)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("un avis sans lot le dit au lieu de sortir un tableau vide")
    void avisSansLot() {
        Map<String, Object> parametres = new HashMap<>();
        parametres.put("AUTORITE", "Autorité de test");
        parametres.put("OBJET", "Travaux");
        parametres.put("FINANCEMENT", "Budget");
        parametres.put("CLAUSES", TextesAvis.clauses(appel(), List.of()));

        byte[] pdf = jasper.versPdf("rapports/avis_appel_offres.jrxml", parametres, List.of());

        assertThat(pdf).isNotEmpty();
    }

    // ------------------------------------------------------------------ fixtures

    private AppelOffres appel() {
        AppelOffres appel = new AppelOffres();
        appel.setRole(AppelOffres.Role.PUBLICATION);
        appel.setNumeroAo("T 001/2026");
        appel.setIntitule("Travaux de construction de deux centres de santé intégrés");
        appel.setAutoriteContractante("Conseil régional de test");
        appel.setFinancement("Budget d'investissement 2026");
        appel.setLigneBudgetaire("9212/2214");
        appel.setModePassation("Appel d'offres ouvert");
        appel.setAdresseRetrait("Direction des services techniques");
        appel.setQualificationsExigees("capacité technique et financière, marchés similaires");
        appel.setPrixDossier(50_000L);
        appel.setMoyenPaiementDossier("mobile money");
        appel.setPlateformeDepot("la plateforme nationale");
        appel.setDateLimiteDepot(Instant.now().plus(45, ChronoUnit.DAYS));
        appel.setOuverturePlis(Instant.now().plus(45, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
        appel.setLieuOuverture("la Direction des services techniques");
        appel.setValiditeAnnonceeJours(120);
        appel.setValiditeOffreJours(120);
        return appel;
    }

    private Lot lot(int numero, Long garantie) {
        Lot lot = new Lot();
        lot.setId(UUID.randomUUID());
        lot.setNumero(numero);
        lot.setIntitule("Travaux de construction, lot " + numero);
        lot.setDelaiMois(4);
        lot.setMontantGarantie(garantie);
        return lot;
    }

    /** Ce que Jasper lit pour le tableau des lots. */
    public record LigneLot(String numero, String designation, String delai) {

        public String getNumero() {
            return numero;
        }

        public String getDesignation() {
            return designation;
        }

        public String getDelai() {
            return delai;
        }
    }
}
