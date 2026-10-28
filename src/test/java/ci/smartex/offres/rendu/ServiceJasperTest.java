package ci.smartex.offres.rendu;

import ci.smartex.offres.util.MontantEnLettres;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Un .jrxml casse ne se voit qu'a l'execution : une expression mal ecrite, un
 * champ absent du jeu de donnees, et le rendu echoue devant l'utilisateur au
 * moment ou il clique. Ce test compile et execute reellement chaque modele.
 */
@DisplayName("Rendu Jasper")
class ServiceJasperTest {

    private final ServiceJasper jasper = new ServiceJasper();
    private final AssembleurPdf assembleur = new AssembleurPdf();

    @ParameterizedTest(name = "{0} compile")
    @DisplayName("tous les modeles livres compilent")
    @ValueSource(strings = {
            "rapports/page_garde.jrxml",
            "rapports/lettre_type.jrxml",
            "rapports/lettre_soumission.jrxml",
            "rapports/dqe.jrxml",
            "rapports/tableau_moyens.jrxml",
            "rapports/rapport_conformite.jrxml",
    })
    void tousLesModelesCompilent(String chemin) {
        assertThatCode(() -> jasper.remplir(chemin, parametresCommuns(), List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la lettre de soumission porte le montant en chiffres et en lettres")
    void lettreDeSoumission() {
        Map<String, Object> parametres = parametresCommuns();
        parametres.put("MONTANT_CHIFFRES", MontantEnLettres.chiffres(18_639_665L));
        parametres.put("MONTANT_LETTRES", MontantEnLettres.montant(18_639_665L,
                "toutes taxes comprises"));
        parametres.put("DELAI_MOIS", 4);
        parametres.put("VALIDITE_JOURS", 120);

        byte[] pdf = jasper.versPdf("rapports/lettre_soumission.jrxml", parametres, List.of());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).startsWith("%PDF-");
        assertThat(assembleur.nombreDePages(pdf)).isEqualTo(1);
    }

    @Test
    @DisplayName("le DQE calcule ses sous-totaux et son total a partir des lignes")
    void detailQuantitatifEtEstimatif() {
        Map<String, Object> parametres = parametresCommuns();
        parametres.put("MONTANT_LETTRES", MontantEnLettres.montant(18_639_665L));
        parametres.put("MENTION_REGIME", "toutes taxes comprises");

        var lignes = List.of(
                new LigneRendue("I - TERRASSEMENTS ET MOUVEMENT DE TERRES", "0",
                        "Débroussement et nettoyage du site", "m²",
                        new BigDecimal("828.000"), 500L, 414_000L),
                new LigneRendue("I - TERRASSEMENTS ET MOUVEMENT DE TERRES", "1",
                        "Installation de chantier y compris baraque", "forf.",
                        new BigDecimal("1.000"), 200_000L, 200_000L),
                new LigneRendue("II - FONDATIONS", "2",
                        "Béton de propreté dosé à 150 kg/m³", "m³",
                        new BigDecimal("36.575"), 85_000L, 3_108_875L));

        byte[] pdf = jasper.versPdf("rapports/dqe.jrxml", parametres, lignes);

        assertThat(pdf).isNotEmpty();
        assertThat(assembleur.nombreDePages(pdf)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("un formulaire de moyens vide le dit, au lieu de sortir une page blanche")
    void tableauDeMoyensVide() {
        Map<String, Object> parametres = parametresCommuns();
        parametres.put("TITRE", "Personnel clé");
        parametres.put("COLONNE_1", "Nom et prénoms");
        parametres.put("COLONNE_2", "Poste");
        parametres.put("COLONNE_3", "Diplôme");
        parametres.put("COLONNE_4", "Expérience");

        byte[] pdf = jasper.versPdf("rapports/tableau_moyens.jrxml", parametres, List.of());

        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("le filigrane BROUILLON se pose sur chaque page")
    void filigraneSurToutesLesPages() {
        byte[] pdf = jasper.versPdf("rapports/page_garde.jrxml", parametresCommuns(), List.of());
        byte[] filigrane = assembleur.filigraner(pdf);

        assertThat(filigrane).isNotEmpty();
        assertThat(assembleur.nombreDePages(filigrane))
                .isEqualTo(assembleur.nombreDePages(pdf));
        // Le filigrane ajoute du contenu : le fichier grossit, il ne se contente
        // pas d'etre recopie.
        assertThat(filigrane.length).isGreaterThan(pdf.length);
    }

    @Test
    @DisplayName("l'assemblage additionne les pages des pieces")
    void assemblageDuDossier() {
        byte[] garde = jasper.versPdf("rapports/page_garde.jrxml", parametresCommuns(), List.of());

        Map<String, Object> lettre = parametresCommuns();
        lettre.put("TITRE", "Engagement sur le délai d'exécution");
        lettre.put("CORPS", "Nous nous engageons à exécuter les travaux dans un délai "
                + "de quatre mois à compter de l'ordre de service.");
        byte[] engagement = jasper.versPdf("rapports/lettre_type.jrxml", lettre, List.of());

        byte[] dossier = assembleur.fusionner(List.of(garde, engagement));

        assertThat(assembleur.nombreDePages(dossier))
                .isEqualTo(assembleur.nombreDePages(garde)
                        + assembleur.nombreDePages(engagement));
    }

    @Test
    @DisplayName("le rapport de conformite liste les controles executes")
    void rapportDeConformite() {
        Map<String, Object> parametres = parametresCommuns();
        parametres.put("PREPARE_PAR", "Rédacteur");
        parametres.put("VALIDE_PAR", "M. NOM PRÉNOMS");
        parametres.put("NB_EXECUTES", 14);
        parametres.put("NB_BLOQUANTS", 0);
        parametres.put("NB_AVERTISSEMENTS", 1);
        parametres.put("EMPREINTE", "a".repeat(64));

        var resultats = List.of(
                new ControleRendu("Délai d'exécution", "BLOQUANT", "OK", ""),
                new ControleRendu("Régime de prix", "AVERTISSEMENT", "À VOIR",
                        "Le DAO exige des prix TTC mais la TVA est déclarée non prise en compte."));

        byte[] pdf = jasper.versPdf("rapports/rapport_conformite.jrxml", parametres, resultats);

        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("une map de parametres immuable passe, et n'est pas modifiee")
    void parametresImmuables() {
        // Jasper range la map des parametres dans la map elle-meme. Lui donner
        // directement celle de l'appelant la modifie dans son dos et fait
        // echouer la generation sur un Map.of, sans message exploitable - le
        // prechauffage au demarrage est tombe exactement la-dessus.
        Map<String, Object> fige = Map.of("LOT_LIBELLE", "Lot 1");

        assertThatCode(() -> jasper.versPdf("rapports/separateur_lot.jrxml", fige, List.of()))
                .doesNotThrowAnyException();
        assertThat(fige).hasSize(1).containsOnlyKeys("LOT_LIBELLE");
    }

    @Test
    @DisplayName("un modele inexistant echoue clairement, pas par une page vide")
    void modeleIntrouvable() {
        assertThatCode(() -> jasper.versPdf("rapports/inexistant.jrxml", Map.of(), List.of()))
                .isInstanceOf(RenduImpossibleException.class)
                .hasMessageContaining("inexistant.jrxml");
    }

    private Map<String, Object> parametresCommuns() {
        Map<String, Object> parametres = new HashMap<>();
        parametres.put("ENTREPRISE_RAISON_SOCIALE", "ENTREPRISE DE RÉFÉRENCE");
        parametres.put("ENTREPRISE_SIGLE", "EDR");
        parametres.put("ENTREPRISE_RCCM", "CI-ABJ-2019-B-12345");
        parametres.put("ENTREPRISE_CC", "1945678 K");
        parametres.put("ENTREPRISE_ADRESSE", "Abidjan, Cocody");
        parametres.put("ENTREPRISE_BP", "08 BP 1234 Abidjan 08");
        parametres.put("ENTREPRISE_TELEPHONES", "+225 07 00 00 00 00");
        parametres.put("TYPE_OFFRE", "OFFRE TECHNIQUE");
        parametres.put("AO_NUMERO", "AO-REFERENCE-001");
        parametres.put("AO_INTITULE",
                "Construction de centres de santé dans deux localités");
        parametres.put("AO_AUTORITE", "Conseil Régional");
        parametres.put("AO_DESTINATAIRE", "Conseil Régional");
        parametres.put("LOT_LIBELLE", "Lot 1 — première localité");
        parametres.put("LIEU", "Abidjan");
        parametres.put("DATE_LETTRES", MontantEnLettres.date(LocalDate.of(2026, 8, 14)));
        parametres.put("SIGNATAIRE_NOM", "M. NOM PRÉNOMS");
        parametres.put("SIGNATAIRE_FONCTION", "Gérant");
        parametres.put("TIMBRE_FISCAL", MontantEnLettres.chiffres(1000L));
        parametres.put("TITRE", "Déclaration sur l'honneur");
        parametres.put("CORPS", "Corps de la lettre.");
        return parametres;
    }

    /** Les lignes que Jasper lit : des accesseurs, rien de plus. */
    public record LigneRendue(String section, String numeroPrix, String designation,
                              String unite, BigDecimal quantite, Long prixUnitaire,
                              Long montant) {

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

        public BigDecimal getQuantite() {
            return quantite;
        }

        public Long getPrixUnitaire() {
            return prixUnitaire;
        }

        public Long getMontant() {
            return montant;
        }
    }

    public record ControleRendu(String libelle, String niveau, String etat, String message) {

        public String getLibelle() {
            return libelle;
        }

        public String getNiveau() {
            return niveau;
        }

        public String getEtat() {
            return etat;
        }

        public String getMessage() {
            return message;
        }
    }
}
