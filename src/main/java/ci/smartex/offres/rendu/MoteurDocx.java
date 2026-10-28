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

    /**
     * Signature d'un document Word 97-2003 : un conteneur OLE2 binaire.
     *
     * <p>Le controle d'extension ne suffit pas. Renommer un .doc en .docx ne
     * le convertit pas, et c'est une manipulation que l'on fait de bonne foi
     * en croyant regler le probleme. Sans ce diagnostic, la plateforme
     * repondait que le fichier n'etait pas un .docx valide - exact, et sans
     * aucune indication sur ce qu'il fallait faire.
     */
    private static final byte[] SIGNATURE_DOC =
            {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
             (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    private static final String CONSEIL_DOC =
            "Ce document est au format Word 97-2003 (.doc), que la plateforme ne "
            + "sait pas remplir. Ouvrez-le dans Word, puis Fichier > Enregistrer "
            + "sous > Document Word (.docx), et redeposez-le. Renommer le fichier "
            + "ne suffit pas : c'est son contenu qui change.";

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

        exigerUnDocx(modele);
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
        exigerUnDocx(modele);
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

    /**
     * Refuse un .doc avant que POI ne s'en charge, pour dire quoi faire.
     *
     * <p>Un .docx est une archive : il commence par PK. Un .doc est un
     * conteneur OLE2. POI distingue les deux, mais son message parle de HSSF
     * et de XSSF - des noms de bibliotheques, qui n'apprennent rien a qui
     * depose un document.
     */
    private void exigerUnDocx(byte[] fichier) {
        if (fichier == null || fichier.length < SIGNATURE_DOC.length) {
            return;
        }
        for (int index = 0; index < SIGNATURE_DOC.length; index++) {
            if (fichier[index] != SIGNATURE_DOC[index]) {
                return;
            }
        }
        throw new RenduImpossibleException(CONSEIL_DOC, null);
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

        List<int[]> champs = new ArrayList<>();
        List<String> remplacements = new ArrayList<>();
        Matcher marque = CHAMP.matcher(texte);
        while (marque.find()) {
            String nom = marque.group(1);
            String valeur = valeurs.get(nom);
            if (valeur == null || valeur.isBlank()) {
                if (!silencieuxSiAbsent) {
                    nonRenseignes.add(nom);
                }
                // Le champ reste visible, et visiblement a completer : un blanc
                // silencieux passerait inapercu jusqu'au depot.
                valeur = "[à compléter : " + nom + "]";
            }
            champs.add(new int[]{marque.start(), marque.end()});
            remplacements.add(valeur);
        }

        // Du dernier champ vers le premier : ce qui est a gauche d'un champ
        // n'est pas deplace par une pose faite a sa droite, donc les positions
        // relevees sur le texte d'origine restent justes jusqu'au bout.
        for (int rang = champs.size() - 1; rang >= 0; rang--) {
            poser(paragraphe, champs.get(rang)[0], champs.get(rang)[1],
                    remplacements.get(rang));
        }
    }

    /**
     * Ecrit une valeur a la place d'un champ, sans toucher au reste du
     * paragraphe.
     *
     * <p>La version precedente reecrivait le paragraphe entier avec la mise
     * en forme de son premier fragment. Sur un vrai document, une ligne melange
     * presque toujours un intitule en gras et une valeur en normal - « Objet
     * du marche : {{OBJET}} » - et tout y passait. Seuls les fragments que le
     * champ occupe sont donc modifies ; la valeur prend la mise en forme du
     * fragment ou le champ commence, c'est-a-dire la sienne.
     *
     * <p>Les reperes sont releves a chaque appel plutot que tenus a jour :
     * un paragraphe compte quelques dizaines de fragments, et une position
     * fausse ecrirait au mauvais endroit dans le document de quelqu'un.
     */
    private void poser(XWPFParagraph paragraphe, int debut, int fin, String valeur) {
        List<XWPFRun> fragments = paragraphe.getRuns();

        // Fragment porteur de texte -> ou il commence. Un fragment sans texte
        // - une image, un saut de ligne - n'y figure pas et reste intact.
        List<int[]> reperes = new ArrayList<>();
        int curseur = 0;
        for (int index = 0; index < fragments.size(); index++) {
            String morceau = fragments.get(index).getText(0);
            if (morceau == null || morceau.isEmpty()) {
                continue;
            }
            reperes.add(new int[]{index, curseur, morceau.length()});
            curseur += morceau.length();
        }

        int premier = -1;
        int dernier = -1;
        for (int rang = 0; rang < reperes.size(); rang++) {
            int ouvre = reperes.get(rang)[1];
            int ferme = ouvre + reperes.get(rang)[2];
            if (premier < 0 && ferme > debut) {
                premier = rang;
            }
            if (premier >= 0) {
                if (ouvre >= fin) {
                    break;
                }
                dernier = rang;
            }
        }
        if (premier < 0) {
            return;
        }

        // De droite a gauche : modifier un fragment ne change pas la position
        // de ceux qui le precedent.
        for (int rang = dernier; rang >= premier; rang--) {
            int[] repere = reperes.get(rang);
            XWPFRun fragment = fragments.get(repere[0]);
            String morceau = fragment.getText(0);
            int ouvre = repere[1];

            String avant = morceau.substring(0, Math.max(0, Math.min(morceau.length(),
                    debut - ouvre)));
            String apres = fin - ouvre < morceau.length()
                    ? morceau.substring(Math.max(0, fin - ouvre))
                    : "";
            // La valeur n'est posee qu'une fois, dans le fragment ou le champ
            // commence ; les suivants ne gardent que ce qui depasse.
            fragment.setText(rang == premier ? avant + valeur + apres : avant + apres, 0);
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
