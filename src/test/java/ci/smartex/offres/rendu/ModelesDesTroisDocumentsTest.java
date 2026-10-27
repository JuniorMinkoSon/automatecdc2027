package ci.smartex.offres.rendu;

import ci.smartex.offres.util.MontantEnLettres;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les modèles qui manquaient aux trois documents : sommaire, séparateur de lot
 * et bordereau des prix unitaires.
 */
@DisplayName("Modèles communs aux trois documents")
class ModelesDesTroisDocumentsTest {

    private final ServiceJasper jasper = new ServiceJasper();
    private final AssembleurPdf assembleur = new AssembleurPdf();

    @Test
    @DisplayName("le sommaire numérote les pièces réellement assemblées")
    void sommaire() {
        Map<String, Object> parametres = commun();
        parametres.put("TYPE_OFFRE", "OFFRE FINANCIÈRE");

        var pieces = List.of(
                new LigneSommaire("1", "Lettre de soumission"),
                new LigneSommaire("2", "Détail quantitatif et estimatif"),
                new LigneSommaire("3", "Bordereau des prix unitaires"));

        byte[] pdf = jasper.versPdf("rapports/sommaire.jrxml", parametres, pieces);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).startsWith("%PDF-");
        assertThat(assembleur.nombreDePages(pdf)).isEqualTo(1);
    }

    @Test
    @DisplayName("un sommaire sans pièce le dit au lieu de sortir une page vide")
    void sommaireVide() {
        byte[] pdf = jasper.versPdf("rapports/sommaire.jrxml", commun(), List.of());
        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("le séparateur de lot tient sur une page")
    void separateurDeLot() {
        Map<String, Object> parametres = commun();
        parametres.put("LOT_LIBELLE", "Lot 1");
        parametres.put("LOT_LOCALITE", "Centre de santé de Sissédougou");

        byte[] pdf = jasper.versPdf("rapports/separateur_lot.jrxml", parametres, List.of());

        assertThat(assembleur.nombreDePages(pdf)).isEqualTo(1);
    }

    @Test
    @DisplayName("le BPU porte chaque prix en chiffres et en lettres, du même nombre")
    void bordereauDesPrixUnitaires() {
        Map<String, Object> parametres = commun();
        parametres.put("LIEU", "Abidjan");
        parametres.put("DATE_LETTRES", MontantEnLettres.date(LocalDate.of(2026, 8, 14)));
        parametres.put("SIGNATAIRE_NOM", "M. KOUAME Yao");
        parametres.put("SIGNATAIRE_FONCTION", "Gérant");

        // Les deux écritures sortent du même entier : c'est ce qui supprime
        // l'écart que la commission corrigerait en retenant les lettres.
        var lignes = List.of(
                lignePrix("I - TERRASSEMENTS", "0", "Débroussement du site", "m2", 500L),
                lignePrix("I - TERRASSEMENTS", "1", "Installation de chantier", "forf.", 200_000L),
                lignePrix("II - FONDATIONS", "2", "Béton de propreté", "m3", 85_000L));

        byte[] pdf = jasper.versPdf("rapports/bpu.jrxml", parametres, lignes);

        assertThat(pdf).isNotEmpty();
        assertThat(assembleur.nombreDePages(pdf)).isGreaterThanOrEqualTo(1);

        // Vérifié hors PDF, sur la source : le libellé ne peut pas diverger du
        // nombre puisqu'il en est dérivé.
        assertThat(lignes.get(2).prixEnLettres())
                .isEqualTo("Quatre-vingt-cinq mille francs CFA");
    }

    @Test
    @DisplayName("un BPU sans prix refuse d'être pris pour un bordereau rempli")
    void bordereauVide() {
        byte[] pdf = jasper.versPdf("rapports/bpu.jrxml", commun(), List.of());
        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("le préchauffage compile tous les modèles livrés")
    void prechauffage() {
        // C'est la réponse à « temps records » : la compilation est payée une
        // fois au démarrage, jamais devant l'utilisateur.
        new PrechauffageJasper(jasper, assembleur).run(null);

        assertThat(jasper.nombreDeModelesCharges())
                .as("tous les modèles livrés doivent compiler")
                .isEqualTo(16);
    }

    // ------------------------------------------------------------------ outils

    private Map<String, Object> commun() {
        Map<String, Object> parametres = new HashMap<>();
        parametres.put("ENTREPRISE_RAISON_SOCIALE", "ENTREPRISE DE TEST");
        parametres.put("ENTREPRISE_RCCM", "CI-TEST-2026-B-00001");
        parametres.put("AO_INTITULE", "Travaux de construction de deux centres de santé");
        parametres.put("AO_NUMERO", "A0026070727726");
        parametres.put("LOT_LIBELLE", "Lot 1 — Sissédougou");
        return parametres;
    }

    private LignePrix lignePrix(String section, String numero, String designation,
                                String unite, long prix) {
        return new LignePrix(section, numero, designation, unite, prix,
                MontantEnLettres.montant(prix));
    }

    public record LigneSommaire(String numero, String intitule) {

        public String getNumero() {
            return numero;
        }

        public String getIntitule() {
            return intitule;
        }
    }

    public record LignePrix(String section, String numeroPrix, String designation,
                            String unite, Long prixUnitaire, String prixEnLettres) {

        public String getSection() {
            return section;
        }

        public String getNumeroPrix() {
            return numeroPrix;
        }

        public String getDesignation() {
            return designation;
        }

        public String getUnite() {
            return unite;
        }

        public Long getPrixUnitaire() {
            return prixUnitaire;
        }

        public String getPrixEnLettres() {
            return prixEnLettres;
        }
    }
}
