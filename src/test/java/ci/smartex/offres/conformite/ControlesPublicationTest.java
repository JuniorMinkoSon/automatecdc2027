package ci.smartex.offres.conformite;

import ci.smartex.offres.domain.AppelOffres;
import ci.smartex.offres.domain.Lot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les contrôles d'un dossier d'appel d'offres avant publication.
 *
 * <p>Les deux premiers cas sortent d'un avis réel. Il annonçait une garantie
 * de « cinq cent cinquante mille (850 000) francs CFA » et une validité des
 * offres de soixante jours là où le règlement en demandait cent vingt. Les
 * candidats qui ont suivi l'avis ont rédigé des offres non conformes sans
 * avoir commis la moindre erreur.
 */
@DisplayName("Contrôles avant publication d'un appel d'offres")
class ControlesPublicationTest {

    @Test
    @DisplayName("une validité annoncée différente de celle du règlement bloque")
    void validiteContradictoire() {
        AppelOffres appel = appelComplet();
        appel.setValiditeAnnonceeJours(60);
        appel.setValiditeOffreJours(120);

        var rapport = ControlesPublication.verifier(appel, List.of(lot(1, 850_000L)));

        assertThat(rapport.publicationPossible()).isFalse();
        assertThat(rapport.bloquantes())
                .extracting(ControlesPublication.Anomalie::message)
                .anyMatch(message -> message.contains("60 jours")
                        && message.contains("120"));
    }

    @Test
    @DisplayName("deux durées identiques passent")
    void validiteCoherente() {
        AppelOffres appel = appelComplet();
        appel.setValiditeAnnonceeJours(120);
        appel.setValiditeOffreJours(120);

        assertThat(ControlesPublication.verifier(appel, List.of(lot(1, 850_000L)))
                .publicationPossible()).isTrue();
    }

    @Test
    @DisplayName("la garantie en lettres et en chiffres sort du même nombre")
    void garantieSansEcartPossible() {
        // C'est la réponse structurelle à « cinq cent cinquante mille (850 000) » :
        // les deux écritures viennent du même entier, par la même fonction.
        String ecrit = ControlesPublication.garantieEnToutesLettres(850_000L);

        assertThat(ecrit)
                .startsWith("huit cent cinquante mille")
                .contains("850 000 F CFA");
    }

    @Test
    @DisplayName("un lot sans montant de garantie bloque")
    void garantieNonChiffree() {
        var rapport = ControlesPublication.verifier(appelComplet(), List.of(lot(1, null)));

        assertThat(rapport.publicationPossible()).isFalse();
        assertThat(rapport.bloquantes())
                .extracting(ControlesPublication.Anomalie::libelle)
                .contains("Garantie non chiffrée");
    }

    @Test
    @DisplayName("ouvrir les plis avant la clôture bloque")
    void ouvertureAvantCloture() {
        AppelOffres appel = appelComplet();
        appel.setOuverturePlis(appel.getDateLimiteDepot().minus(1, ChronoUnit.DAYS));

        var rapport = ControlesPublication.verifier(appel, List.of(lot(1, 850_000L)));

        assertThat(rapport.bloquantes())
                .extracting(ControlesPublication.Anomalie::libelle)
                .contains("Ouverture avant la clôture");
    }

    @Test
    @DisplayName("un délai de publicité trop court avertit sans bloquer")
    void publiciteTropCourte() {
        AppelOffres appel = appelComplet();
        appel.setDateLimiteDepot(Instant.now().plus(5, ChronoUnit.DAYS));
        appel.setOuverturePlis(Instant.now().plus(6, ChronoUnit.DAYS));

        var rapport = ControlesPublication.verifier(appel, List.of(lot(1, 850_000L)));

        // Un délai court peut se justifier : c'est une alerte, pas un refus.
        assertThat(rapport.publicationPossible()).isTrue();
        assertThat(rapport.avertissements())
                .extracting(ControlesPublication.Anomalie::libelle)
                .contains("Délai de publicité court");
    }

    @Test
    @DisplayName("les mentions obligatoires manquantes sont listées en une fois")
    void mentionsObligatoires() {
        AppelOffres appel = new AppelOffres();
        appel.setIntitule("Travaux de construction");
        appel.setDateLimiteDepot(Instant.now().plus(60, ChronoUnit.DAYS));

        var rapport = ControlesPublication.verifier(appel, List.of());

        assertThat(rapport.publicationPossible()).isFalse();
        assertThat(rapport.bloquantes())
                .extracting(ControlesPublication.Anomalie::message)
                .anyMatch(message -> message.contains("numéro d'appel d'offres")
                        && message.contains("autorité contractante")
                        && message.contains("au moins un lot"));
    }

    // ------------------------------------------------------------------ fixtures

    private AppelOffres appelComplet() {
        AppelOffres appel = new AppelOffres();
        appel.setRole(AppelOffres.Role.PUBLICATION);
        appel.setNumeroAo("T 001/2026");
        appel.setIntitule("Travaux de construction de deux centres de santé");
        appel.setAutoriteContractante("Conseil régional de test");
        appel.setFinancement("Budget d'investissement 2026");
        appel.setModePassation("Appel d'offres ouvert");
        appel.setAdresseRetrait("Direction des services techniques");
        appel.setDateLimiteDepot(Instant.now().plus(60, ChronoUnit.DAYS));
        appel.setOuverturePlis(Instant.now().plus(60, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
        return appel;
    }

    private Lot lot(int numero, Long garantie) {
        Lot lot = new Lot();
        lot.setId(UUID.randomUUID());
        lot.setNumero(numero);
        lot.setIntitule("Lot " + numero);
        lot.setDelaiMois(4);
        lot.setMontantGarantie(garantie);
        return lot;
    }
}
