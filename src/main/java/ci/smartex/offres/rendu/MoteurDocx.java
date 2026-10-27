package ci.smartex.offres.rendu;

import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remplit un modele Word en conservant sa mise en page.
 *
 * <p>Le principe est l'inverse de celui d'un generateur classique : on
 * n'ecrit pas un document, on en ouvre un et on y remplace des champs. Un
 * dossier reconstruit de zero ne ressemble plus a celui que la commission a
 * l'habitude de lire, et c'est elle qui decide. Les tableaux, les en-tetes,
 * les polices et les sauts de page du modele sont donc preserves tels quels.
 *
 * <p>Deux marques sont reconnues :
 * <ul>
 *   <li>{@code {{CHAMP}}} : remplace par une valeur ;</li>
 *   <li>{@code {{#LISTE:nom}}} dans une cellule : la ligne du tableau est
 *       repetee une fois par element, et ses champs sont lus dans l'element.</li>
 * </ul>
 *
 * <p>Word decoupe souvent un champ en plusieurs fragments internes : ecrire
 * « {{NOM}} » peut produire « {{ », « NOM », « }} ». Un remplacement naif sur
 * chaque fragment ne trouverait jamais rien. Les fragments d'un paragraphe
 * sont donc reunis avant remplacement, et seulement dans les paragraphes qui
 * contiennent reellement un champ, pour ne pas toucher a la mise en forme des
 * autres.
 */
@Service
public class MoteurDocx {

    /** {{CHAMP}}, avec espaces tolerés autour du nom. */
    private static final Pattern CHAMP = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*}}");

    /** {{#LISTE:nom}} en tete de ligne repetable. */
    private static final Pattern MARQUE_LISTE =
            Pattern.compile("\\{\\{\\s*#LISTE\\s*:\\s*([A-Za-z0-9_]+)\\s*}}");

    /**
     * Remplit un modele.
     *
     * @param modele  le .docx televerse, inchange
     * @param champs  les valeurs simples
     * @param listes  les lignes repetables, par nom de liste
     * @return le document rempli et la liste des champs restes sans valeur
     */
    public Resultat remplir(byte[] modele,
                            Map<String, String> champs,
                            Map<String, List<Map<String, String>>> listes) {

        Set<String> nonRenseignes = new TreeSet<>();

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(modele))) {

            for (XWPFTable tableau : document.getTables()) {
                developperListes(tableau, listes, nonRenseignes);
            }
            remplacerPartout(document, champs, nonRenseignes);

            var sortie = new ByteArrayOutputStream();
            document.write(sortie);
            return new Resultat(sortie.toByteArray(), List.copyOf(nonRenseignes));

        } catch (RenduImpossibleException erreur) {
            throw erreur;
        } catch (Exception erreur) {
            // POI leve des exceptions non verifiees sur un fichier qui n'est
            // pas un document Office. Un televersement douteux doit donner un
            // message lisible, pas une trace d'execution.
            throw new RenduImpossibleException(
                    "Ce modele Word n'a pas pu etre rempli. Est-il bien au format .docx ?",
                    erreur);
        }
    }

    /**
     * Les champs qu'un modele attend.
     *
     * <p>C'est ce qui permet au formulaire de ne demander que les informations
     * reellement utiles au marche : on lit le modele plutot que de coder en
     * dur une liste de questions qui vieillirait mal.
     */
    public Set<String> champsAttendus(byte[] modele) {
        Set<String> trouves = new TreeSet<>();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(modele))) {
            parcourirTextes(document, texte -> {
                Matcher marque = CHAMP.matcher(texte);
                while (marque.find()) {
                    String nom = marque.group(1);
                    if (!nom.startsWith("#")) {
                        trouves.add(nom);
                    }
                }
            });
        } catch (Exception erreur) {
            throw new RenduImpossibleException(
                    "Lecture impossible : ce fichier n'est pas un modele .docx valide.",
                    erreur);
        }
        return trouves;
    }

    // ------------------------------------------------------------------ listes

    /**
     * Repete la ligne marquee une fois par element, puis supprime le modele de
     * ligne. Une liste vide laisse une ligne qui dit qu'elle est vide, plutot
     * qu'un tableau sans en-tete de donnees : un formulaire officiel vide doit
     * se voir, pas disparaitre.
     */
    private void developperListes(XWPFTable tableau,
                                  Map<String, List<Map<String, String>>> listes,
                                  Set<String> nonRenseignes) {

        for (int index = 0; index < tableau.getNumberOfRows(); index++) {
            XWPFTableRow ligne = tableau.getRow(index);
            String texte = texteDeLaLigne(ligne);

            Matcher marque = MARQUE_LISTE.matcher(texte);
            if (!marque.find()) {
                continue;
            }

            String nomListe = marque.group(1);
            List<Map<String, String>> elements =
                    listes.getOrDefault(nomListe, List.of());

            if (elements.isEmpty()) {
                nonRenseignes.add(nomListe + " (aucun element)");
                effacerMarque(ligne);
                continue;
            }

            int insertion = index + 1;
            for (Map<String, String> element : elements) {
                XWPFTableRow copie = new XWPFTableRow(
                        (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow)
                                ligne.getCtRow().copy(), tableau);
                retirerMarqueListe(copie);
                remplacerDansLigne(copie, element, nonRenseignes);
                tableau.addRow(copie, insertion++);
            }

            // La ligne modele a joue son role : elle disparait, et les copies
            // qui la suivaient remontent d'un cran. On reprend le parcours
            // apres la derniere d'entre elles.
            tableau.removeRow(index);
            index += elements.size() - 1;
        }
    }

    /**
     * Enleve la marque {@code {{#LISTE:nom}}} d'une copie de ligne. Sans cela
     * elle resterait imprimee dans le dossier, puisque ce n'est pas un champ
     * ordinaire et qu'aucun remplacement ne la vise.
     */
    private void retirerMarqueListe(XWPFTableRow ligne) {
        for (XWPFTableCell cellule : ligne.getTableCells()) {
            for (XWPFParagraph paragraphe : cellule.getParagraphs()) {
                String texte = texteDuParagraphe(paragraphe);
                Matcher marque = MARQUE_LISTE.matcher(texte);
                if (marque.find()) {
                    ecrireParagraphe(paragraphe, marque.replaceAll("").trim());
                }
            }
        }
    }

    private void effacerMarque(XWPFTableRow ligne) {
        for (XWPFTableCell cellule : ligne.getTableCells()) {
            for (XWPFParagraph paragraphe : cellule.getParagraphs()) {
                String texte = texteDuParagraphe(paragraphe);
                if (MARQUE_LISTE.matcher(texte).find()) {
                    ecrireParagraphe(paragraphe, "Néant");
                }
            }
        }
    }

    private void remplacerDansLigne(XWPFTableRow ligne,
                                    Map<String, String> valeurs,
                                    Set<String> nonRenseignes) {
        for (XWPFTableCell cellule : ligne.getTableCells()) {
            for (XWPFParagraph paragraphe : cellule.getParagraphs()) {
                remplacerDansParagraphe(paragraphe, valeurs, nonRenseignes, true);
            }
        }
    }

    private String texteDeLaLigne(XWPFTableRow ligne) {
        StringBuilder texte = new StringBuilder();
        for (XWPFTableCell cellule : ligne.getTableCells()) {
            texte.append(cellule.getText()).append(' ');
        }
        return texte.toString();
    }

    // ------------------------------------------------------------------ champs

    private void remplacerPartout(XWPFDocument document,
                                  Map<String, String> champs,
                                  Set<String> nonRenseignes) {

        for (XWPFParagraph paragraphe : document.getParagraphs()) {
            remplacerDansParagraphe(paragraphe, champs, nonRenseignes, false);
        }
        for (XWPFTable tableau : document.getTables()) {
            for (XWPFTableRow ligne : tableau.getRows()) {
                for (XWPFTableCell cellule : ligne.getTableCells()) {
                    for (XWPFParagraph paragraphe : cellule.getParagraphs()) {
                        remplacerDansParagraphe(paragraphe, champs, nonRenseignes, false);
                    }
                }
            }
        }
        // En-tetes et pieds de page portent souvent le nom de l'entreprise et
        // son RCCM : les oublier laisserait l'identite du modele d'origine sur
        // chaque page, ce qui serait pire qu'un champ vide.
        for (XWPFHeader entete : document.getHeaderList()) {
            entete.getParagraphs().forEach(p ->
                    remplacerDansParagraphe(p, champs, nonRenseignes, false));
        }
        for (XWPFFooter pied : document.getFooterList()) {
            pied.getParagraphs().forEach(p ->
                    remplacerDansParagraphe(p, champs, nonRenseignes, false));
        }
    }

    private void remplacerDansParagraphe(XWPFParagraph paragraphe,
                                         Map<String, String> valeurs,
                                         Set<String> nonRenseignes,
                                         boolean silencieuxSiAbsent) {

        String texte = texteDuParagraphe(paragraphe);
        if (texte.isEmpty() || !texte.contains("{{")) {
            // Aucun champ : on ne touche a rien, donc la mise en forme fine du
            // paragraphe est integralement preservee.
            return;
        }

        StringBuilder resultat = new StringBuilder();
        Matcher marque = CHAMP.matcher(texte);
        int curseur = 0;
        boolean modifie = false;

        while (marque.find()) {
            resultat.append(texte, curseur, marque.start());
            String nom = marque.group(1);
            String valeur = valeurs.get(nom);

            if (valeur == null || valeur.isBlank()) {
                if (!silencieuxSiAbsent) {
                    nonRenseignes.add(nom);
                }
                // Le champ reste visible, et visiblement a completer : un blanc
                // silencieux passerait inapercu jusqu'au depot.
                resultat.append("[à compléter : ").append(nom).append(']');
            } else {
                resultat.append(valeur);
            }
            curseur = marque.end();
            modifie = true;
        }
        resultat.append(texte.substring(curseur));

        if (modifie) {
            ecrireParagraphe(paragraphe, resultat.toString());
        }
    }

    private String texteDuParagraphe(XWPFParagraph paragraphe) {
        StringBuilder texte = new StringBuilder();
        for (XWPFRun fragment : paragraphe.getRuns()) {
            String morceau = fragment.getText(0);
            if (morceau != null) {
                texte.append(morceau);
            }
        }
        return texte.toString();
    }

    /**
     * Reecrit le paragraphe en gardant la mise en forme de son premier
     * fragment, qui est celle du champ lui-meme dans la quasi-totalite des
     * modeles.
     */
    private void ecrireParagraphe(XWPFParagraph paragraphe, String texte) {
        List<XWPFRun> fragments = paragraphe.getRuns();
        if (fragments.isEmpty()) {
            paragraphe.createRun().setText(texte, 0);
            return;
        }

        XWPFRun premier = fragments.get(0);
        for (int index = fragments.size() - 1; index >= 1; index--) {
            paragraphe.removeRun(index);
        }

        // Les sauts de ligne internes doivent rester des sauts de ligne Word,
        // sans quoi un texte sur plusieurs lignes arrive colle en une seule.
        String[] lignes = texte.split("\n", -1);
        premier.setText(lignes[0], 0);
        for (int index = 1; index < lignes.length; index++) {
            premier.addBreak();
            premier.setText(lignes[index]);
        }
    }

    private void parcourirTextes(XWPFDocument document, java.util.function.Consumer<String> vu) {
        document.getParagraphs().forEach(p -> vu.accept(texteDuParagraphe(p)));
        for (XWPFTable tableau : document.getTables()) {
            for (XWPFTableRow ligne : tableau.getRows()) {
                for (XWPFTableCell cellule : ligne.getTableCells()) {
                    vu.accept(cellule.getText());
                }
            }
        }
        document.getHeaderList().forEach(e ->
                e.getParagraphs().forEach(p -> vu.accept(texteDuParagraphe(p))));
        document.getFooterList().forEach(f ->
                f.getParagraphs().forEach(p -> vu.accept(texteDuParagraphe(p))));
    }

    /**
     * Le document rempli, et ce qui lui manque.
     *
     * @param champsNonRenseignes ce que l'utilisateur doit encore fournir. La
     *                            liste part a l'ecran : un dossier incomplet
     *                            doit le dire, pas le cacher.
     */
    public record Resultat(byte[] document, List<String> champsNonRenseignes) {

        public boolean complet() {
            return champsNonRenseignes.isEmpty();
        }
    }
}
