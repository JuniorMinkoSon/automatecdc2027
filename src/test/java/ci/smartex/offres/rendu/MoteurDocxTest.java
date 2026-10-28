package ci.smartex.offres.rendu;

import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le moteur Word se teste sur de vrais fichiers .docx, fabriques ici puis
 * relus apres remplissage. Verifier seulement qu'aucune exception n'est levee
 * ne dirait rien : ce qui compte est le contenu du document produit.
 */
@DisplayName("Moteur de documents Word")
class MoteurDocxTest {

    private final MoteurDocx moteur = new MoteurDocx();

    @Test
    @DisplayName("un champ est remplace par sa valeur")
    void champSimple() throws Exception {
        byte[] modele = modele(document -> {
            paragraphe(document, "Je soussigné {{SIGNATAIRE}}, {{FONCTION}} de "
                    + "l'entreprise {{ENTREPRISE}}.");
        });

        var resultat = moteur.remplir(modele, Map.of(
                "SIGNATAIRE", "M. KOUAME Yao",
                "FONCTION", "Gérant",
                "ENTREPRISE", "Entreprise de test"), Map.of());

        assertThat(texte(resultat.document()))
                .contains("Je soussigné M. KOUAME Yao, Gérant de l'entreprise "
                        + "Entreprise de test.")
                .doesNotContain("{{");
        assertThat(resultat.complet()).isTrue();
    }

    @Test
    @DisplayName("un champ decoupe par Word en plusieurs fragments est quand meme trouve")
    void champDecoupeEnFragments() throws Exception {
        // C'est le cas reel : Word coupe « {{NOM}} » des qu'on a corrige une
        // lettre au milieu. Un remplacement fragment par fragment echouerait.
        byte[] modele = modele(document -> {
            XWPFParagraph paragraphe = document.createParagraph();
            paragraphe.createRun().setText("Entreprise : {{");
            paragraphe.createRun().setText("ENTREPRISE");
            paragraphe.createRun().setText("}}");
        });

        var resultat = moteur.remplir(modele,
                Map.of("ENTREPRISE", "Entreprise de test"), Map.of());

        assertThat(texte(resultat.document())).contains("Entreprise : Entreprise de test");
    }

    @Test
    @DisplayName("un champ sans valeur reste visible et est signale")
    void champNonRenseigne() throws Exception {
        byte[] modele = modele(document ->
                paragraphe(document, "Numéro RCCM : {{RCCM}}"));

        var resultat = moteur.remplir(modele, Map.of(), Map.of());

        // Un blanc silencieux passerait jusqu'au depot. Mieux vaut qu'il se voie.
        assertThat(texte(resultat.document())).contains("[à compléter : RCCM]");
        assertThat(resultat.champsNonRenseignes()).containsExactly("RCCM");
        assertThat(resultat.complet()).isFalse();
    }

    @Test
    @DisplayName("une ligne de tableau marquee se repete une fois par element")
    void lignesRepetees() throws Exception {
        byte[] modele = modele(document -> {
            XWPFTable tableau = document.createTable(2, 2);
            cellule(tableau, 0, 0, "Nom");
            cellule(tableau, 0, 1, "Poste");
            cellule(tableau, 1, 0, "{{#LISTE:personnel}}{{nom}}");
            cellule(tableau, 1, 1, "{{poste}}");
        });

        var resultat = moteur.remplir(modele, Map.of(), Map.of("personnel", List.of(
                Map.of("nom", "KOUAME Yao", "poste", "Conducteur des travaux"),
                Map.of("nom", "TRAORE Awa", "poste", "Chef de chantier"))));

        String texte = texte(resultat.document());
        assertThat(texte)
                .contains("KOUAME Yao").contains("Conducteur des travaux")
                .contains("TRAORE Awa").contains("Chef de chantier")
                .doesNotContain("#LISTE")
                .doesNotContain("{{");

        assertThat(lignesDuPremierTableau(resultat.document()))
                .as("en-tete plus une ligne par personne")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("une liste vide le dit au lieu de laisser un tableau trompeur")
    void listeVide() throws Exception {
        byte[] modele = modele(document -> {
            XWPFTable tableau = document.createTable(2, 1);
            cellule(tableau, 0, 0, "Matériel");
            cellule(tableau, 1, 0, "{{#LISTE:materiel}}{{designation}}");
        });

        var resultat = moteur.remplir(modele, Map.of(), Map.of());

        assertThat(texte(resultat.document())).contains("Néant").doesNotContain("#LISTE");
        assertThat(resultat.champsNonRenseignes())
                .anyMatch(champ -> champ.contains("materiel"));
    }

    @Test
    @DisplayName("les champs d'un modele se lisent sans le remplir")
    void champsAttendus() throws Exception {
        byte[] modele = modele(document -> {
            paragraphe(document, "{{ENTREPRISE}} — {{RCCM}}");
            XWPFTable tableau = document.createTable(1, 1);
            cellule(tableau, 0, 0, "{{AO_NUMERO}}");
        });

        // C'est ainsi que le formulaire sait quoi demander : il lit le modele,
        // au lieu de porter une liste de questions codee en dur qui vieillirait.
        assertThat(moteur.champsAttendus(modele))
                .containsExactlyInAnyOrder("ENTREPRISE", "RCCM", "AO_NUMERO");
    }

    @Test
    @DisplayName("un paragraphe sans champ n'est pas touche")
    void miseEnFormePreservee() throws Exception {
        byte[] modele = modele(document -> {
            XWPFParagraph paragraphe = document.createParagraph();
            XWPFRun gras = paragraphe.createRun();
            gras.setText("RÉPUBLIQUE DE CÔTE D'IVOIRE");
            gras.setBold(true);
            paragraphe(document, "Entreprise : {{ENTREPRISE}}");
        });

        var resultat = moteur.remplir(modele, Map.of("ENTREPRISE", "X"), Map.of());

        try (XWPFDocument relu = new XWPFDocument(
                new ByteArrayInputStream(resultat.document()))) {
            XWPFRun premier = relu.getParagraphs().get(0).getRuns().get(0);
            assertThat(premier.isBold())
                    .as("le titre en gras ne contient aucun champ, il ne doit pas bouger")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("un fichier qui n'est pas un .docx echoue clairement")
    void fichierInvalide() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                moteur.remplir("ceci n'est pas un document".getBytes(), Map.of(), Map.of())))
                .isInstanceOf(RenduImpossibleException.class)
                .hasMessageContaining(".docx");
    }

    @Test
    @DisplayName("un intitule en gras reste en gras quand le champ qui le suit est rempli")
    void laMiseEnFormeDeLaLigneSurvit() throws Exception {
        byte[] modele = modele(document -> {
            XWPFParagraph paragraphe = document.createParagraph();
            XWPFRun intitule = paragraphe.createRun();
            intitule.setBold(true);
            intitule.setText("Objet du marché : ");
            XWPFRun champ = paragraphe.createRun();
            champ.setText("{{OBJET}}");
            XWPFRun fin = paragraphe.createRun();
            fin.setItalic(true);
            fin.setText(" (lot unique)");
        });

        byte[] rempli = moteur.remplir(modele,
                Map.of("OBJET", "Construction de deux centres de santé"),
                Map.of()).document();

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rempli))) {
            XWPFParagraph paragraphe = document.getParagraphs().get(0);
            String texte = paragraphe.getText();
            assertThat(texte).isEqualTo(
                    "Objet du marché : Construction de deux centres de santé (lot unique)");

            // Le fond du test : la ligne melange trois mises en forme, et le
            // remplacement ne doit en ecraser aucune.
            List<XWPFRun> fragments = paragraphe.getRuns();
            XWPFRun gras = fragments.stream()
                    .filter(f -> "Objet du marché : ".equals(f.getText(0)))
                    .findFirst().orElseThrow();
            assertThat(gras.isBold()).as("l'intitule etait en gras").isTrue();

            XWPFRun valeur = fragments.stream()
                    .filter(f -> "Construction de deux centres de santé".equals(f.getText(0)))
                    .findFirst().orElseThrow();
            assertThat(valeur.isBold()).as("la valeur prend la forme du champ, pas du titre")
                    .isFalse();

            XWPFRun italique = fragments.stream()
                    .filter(f -> " (lot unique)".equals(f.getText(0)))
                    .findFirst().orElseThrow();
            assertThat(italique.isItalic()).as("la fin de ligne etait en italique").isTrue();
        }
    }

    @Test
    @DisplayName("deux champs dans un meme paragraphe se remplissent tous les deux")
    void deuxChampsDansLeMemeParagraphe() throws Exception {
        byte[] modele = modele(document -> {
            XWPFParagraph paragraphe = document.createParagraph();
            // Word coupe volontiers un champ en plusieurs fragments : on
            // reproduit exactement ce decoupage.
            for (String morceau : List.of("Lot ", "{{", "NUMERO", "}}", " — ",
                                          "{{LOCALITE}}", " (", "{{DELAI}}", " mois)")) {
                paragraphe.createRun().setText(morceau);
            }
        });

        byte[] rempli = moteur.remplir(modele,
                Map.of("NUMERO", "1", "LOCALITE", "Première localité", "DELAI", "4"),
                Map.of()).document();

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rempli))) {
            assertThat(document.getParagraphs().get(0).getText())
                    .isEqualTo("Lot 1 — Première localité (4 mois)");
        }
    }

    @Test
    @DisplayName("un .doc renomme en .docx dit quoi faire, pas seulement que c'est refuse")
    void documentWordAncienFormat() {
        // Les huit premiers octets d'un document Word 97-2003 : un conteneur
        // OLE2. Renommer un tel fichier en .docx passe le controle
        // d'extension, et c'est une manipulation que l'on fait de bonne foi.
        byte[] ancienFormat = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                               (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1,
                               0x00, 0x00, 0x00, 0x00};

        for (Runnable appel : List.<Runnable>of(
                () -> moteur.remplir(ancienFormat, Map.of(), Map.of()),
                () -> moteur.champsAttendus(ancienFormat))) {

            assertThat(org.assertj.core.api.Assertions.catchThrowable(appel::run))
                    .isInstanceOf(RenduImpossibleException.class)
                    .hasMessageContaining("Word 97-2003")
                    .hasMessageContaining("Enregistrer")
                    .as("renommer ne convertit pas, et le message doit le dire")
                    .hasMessageContaining("Renommer");
        }
    }

    // ------------------------------------------------------------------ outils

    private interface Constructeur {
        void construire(XWPFDocument document);
    }

    private byte[] modele(Constructeur constructeur) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             var sortie = new ByteArrayOutputStream()) {
            constructeur.construire(document);
            document.write(sortie);
            return sortie.toByteArray();
        }
    }

    private void paragraphe(XWPFDocument document, String texte) {
        document.createParagraph().createRun().setText(texte);
    }

    private void cellule(XWPFTable tableau, int ligne, int colonne, String texte) {
        XWPFTableCell cellule = tableau.getRow(ligne).getCell(colonne);
        cellule.removeParagraph(0);
        cellule.addParagraph().createRun().setText(texte);
    }

    private String texte(byte[] docx) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            StringBuilder texte = new StringBuilder();
            document.getParagraphs().forEach(p -> texte.append(p.getText()).append('\n'));
            for (XWPFTable tableau : document.getTables()) {
                for (XWPFTableRow ligne : tableau.getRows()) {
                    for (XWPFTableCell cellule : ligne.getTableCells()) {
                        texte.append(cellule.getText()).append(" | ");
                    }
                    texte.append('\n');
                }
            }
            return texte.toString();
        }
    }

    private int lignesDuPremierTableau(byte[] docx) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            return document.getTables().get(0).getNumberOfRows();
        }
    }
}
