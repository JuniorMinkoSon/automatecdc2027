package ci.smartex.offres.rendu;

import ci.smartex.offres.domain.AppelOffres;
import ci.smartex.offres.domain.DonneeDpao;
import ci.smartex.offres.domain.Lot;
import ci.smartex.offres.util.MontantEnLettres;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * La liste des donnees particulieres de l'appel d'offres, dans l'ordre des
 * instructions aux candidats.
 *
 * <p>Le DPAO n'est pas de la prose : c'est une suite de reponses numerotees,
 * chacune rattachee a un article. Le saisir dans une zone de texte libre
 * reviendrait a retaper un formulaire, c'est-a-dire a reproduire exactement ce
 * que la plateforme sert a supprimer.
 *
 * <p>Certaines reponses se deduisent du marche : l'objet, les lots, les delais,
 * la garantie, les dates. Les recopier ici les ferait diverger au premier
 * changement, elles sont donc calculees a la generation et marquees comme
 * derivees - l'autorite contractante ne les saisit pas, et ne peut donc pas
 * les contredire.
 */
public final class CharpenteDpao {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");
    private static final DateTimeFormatter JOUR_ET_HEURE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH'h'mm", Locale.FRENCH);

    private CharpenteDpao() {
    }

    /**
     * Les articles du DPAO. {@code derivee} marque ceux dont la valeur vient
     * du marche lui-meme.
     */
    public static final List<Article> ARTICLES = List.of(
            new Article("IC 1.1", "Objet du marché et numéro de l'appel d'offres", true),
            new Article("IC 1.1 b", "Nombre et identification des lots", true),
            new Article("IC 2.1", "Origine des fonds", true),
            new Article("IC 4.1", "Pré-qualification préalable", false),
            new Article("IC 5.1", "Conditions de qualification exigées", true),
            new Article("IC 7.1", "Adresse pour les demandes d'éclaircissement", true),
            new Article("IC 7.4", "Réunion préparatoire : lieu et date", false),
            new Article("IC 11.1 j", "Documents complémentaires exigés", false),
            new Article("IC 13.1", "Variantes autorisées", false),
            new Article("IC 13.2", "Délai d'exécution maximum", true),
            new Article("IC 14.5", "Révision des prix", false),
            new Article("IC 15.1", "Monnaie de l'offre", false),
            new Article("IC 19.1", "Durée de validité des offres", true),
            new Article("IC 20.1", "Montant du cautionnement provisoire", true),
            new Article("IC 21.1", "Nombre de copies de l'offre", false),
            new Article("IC 22.5 b", "Identification à porter sur les enveloppes", true),
            new Article("IC 23.1", "Adresse, date et heure limites de dépôt", true),
            new Article("IC 26.1", "Lieu, date et heure d'ouverture des plis", true),
            new Article("IC 35.1", "Marge de préférence", false),
            new Article("IC 36.1", "Sous-traitance autorisée et plafond", false));

    /**
     * La valeur d'un article derive, calculee a partir du marche.
     *
     * <p>Rend {@code null} pour un article que l'autorite contractante doit
     * saisir elle-meme.
     */
    public static String valeurDerivee(String referenceIc, AppelOffres appel, List<Lot> lots) {
        return switch (referenceIc) {
            case "IC 1.1" -> appel.getIntitule()
                    + (appel.getNumeroAo() == null || appel.getNumeroAo().isBlank()
                       ? "" : "\nAppel d'offres n° " + appel.getNumeroAo());

            case "IC 1.1 b" -> lots.isEmpty()
                    ? "Marché non alloti."
                    : "%d lot(s) :\n".formatted(lots.size()) + lots.stream()
                            .map(lot -> "  - lot %d : %s%s".formatted(lot.getNumero(),
                                    lot.getIntitule(),
                                    lot.getLocalite() == null || lot.getLocalite().isBlank()
                                            ? "" : " (" + lot.getLocalite() + ")"))
                            .reduce((a, b) -> a + "\n" + b).orElse("");

            case "IC 2.1" -> appel.getFinancement()
                    + (appel.getLigneBudgetaire() == null || appel.getLigneBudgetaire().isBlank()
                       ? "" : ", ligne " + appel.getLigneBudgetaire());

            case "IC 5.1" -> appel.getQualificationsExigees();

            case "IC 7.1" -> appel.getAdresseRetrait()
                    + (appel.getContacts() == null || appel.getContacts().isBlank()
                       ? "" : "\n" + appel.getContacts());

            case "IC 13.2" -> delais(lots);

            case "IC 19.1" -> dureeValidite(appel);

            case "IC 20.1" -> garanties(lots);

            case "IC 22.5 b" -> appel.getNumeroAo() == null || appel.getNumeroAo().isBlank()
                    ? null
                    : "« Appel d'offres n° %s — offre à n'ouvrir qu'en séance d'ouverture »"
                            .formatted(appel.getNumeroAo());

            case "IC 23.1" -> depot(appel);

            case "IC 26.1" -> ouverture(appel);

            default -> null;
        };
    }

    // ------------------------------------------------------------------ valeurs

    private static String delais(List<Lot> lots) {
        if (lots.isEmpty()) {
            return null;
        }
        boolean memeDelai = lots.stream()
                .map(Lot::getDelaiMois)
                .distinct()
                .count() == 1;

        if (memeDelai && lots.get(0).getDelaiMois() != null) {
            int delai = lots.get(0).getDelaiMois();
            return "%s (%02d) mois pour chaque lot."
                    .formatted(MontantEnLettres.nombre(delai), delai);
        }
        return lots.stream()
                .map(lot -> "  - lot %d : %s".formatted(lot.getNumero(),
                        lot.getDelaiMois() == null
                                ? "[délai à fixer]"
                                : "%s (%02d) mois".formatted(
                                        MontantEnLettres.nombre(lot.getDelaiMois()),
                                        lot.getDelaiMois())))
                .reduce((a, b) -> a + "\n" + b).orElse(null);
    }

    /**
     * Lue une seule fois. Le controle de publication refuse que la duree
     * annoncee et celle du reglement different ; cet article, lui, ne peut pas
     * les faire diverger puisqu'il n'en connait qu'une.
     */
    private static String dureeValidite(AppelOffres appel) {
        Integer jours = appel.getValiditeOffreJours() != null
                ? appel.getValiditeOffreJours()
                : appel.getValiditeAnnonceeJours();
        return jours == null
                ? null
                : "%s (%d) jours à compter de la date limite de dépôt."
                        .formatted(MontantEnLettres.nombre(jours), jours);
    }

    private static String garanties(List<Lot> lots) {
        List<Long> montants = lots.stream()
                .map(Lot::getMontantGarantie)
                .filter(montant -> montant != null)
                .distinct()
                .toList();

        if (montants.isEmpty()) {
            return null;
        }
        if (montants.size() == 1) {
            long montant = montants.get(0);
            return "%s (%s) par lot.".formatted(
                    MontantEnLettres.nombre(montant), MontantEnLettres.chiffres(montant));
        }
        return lots.stream()
                .filter(lot -> lot.getMontantGarantie() != null)
                .map(lot -> "  - lot %d : %s (%s)".formatted(lot.getNumero(),
                        MontantEnLettres.nombre(lot.getMontantGarantie()),
                        MontantEnLettres.chiffres(lot.getMontantGarantie())))
                .reduce((a, b) -> a + "\n" + b).orElse(null);
    }

    private static String depot(AppelOffres appel) {
        if (appel.getDateLimiteDepot() == null) {
            return null;
        }
        String ou = appel.getPlateformeDepot() == null || appel.getPlateformeDepot().isBlank()
                ? (appel.getAdresseRetrait() == null ? "" : appel.getAdresseRetrait())
                : appel.getPlateformeDepot()
                  + (appel.getUrlDepot() == null || appel.getUrlDepot().isBlank()
                     ? "" : " (" + appel.getUrlDepot() + ")");
        return (ou.isBlank() ? "" : ou + "\n")
                + appel.getDateLimiteDepot().atZone(ABIDJAN).format(JOUR_ET_HEURE)
                + " (temps universel)";
    }

    private static String ouverture(AppelOffres appel) {
        if (appel.getOuverturePlis() == null) {
            return null;
        }
        return (appel.getLieuOuverture() == null || appel.getLieuOuverture().isBlank()
                ? "" : appel.getLieuOuverture() + "\n")
                + appel.getOuverturePlis().atZone(ABIDJAN).format(JOUR_ET_HEURE)
                + " (temps universel)";
    }

    /** Le texte complet du DPAO, pour la section du dossier. */
    public static String texte(List<DonneeDpao> donnees, AppelOffres appel, List<Lot> lots) {
        StringBuilder sortie = new StringBuilder();
        for (DonneeDpao donnee : donnees) {
            String valeur = donnee.isDerivee()
                    ? valeurDerivee(donnee.getReferenceIc(), appel, lots)
                    : donnee.getValeur();

            sortie.append(donnee.getReferenceIc()).append("   ")
                    .append(donnee.getLibelle()).append('\n')
                    .append("        ")
                    .append(valeur == null || valeur.isBlank()
                            ? "[à compléter]"
                            : valeur.replace("\n", "\n        "))
                    .append("\n\n");
        }
        return sortie.toString();
    }

    public record Article(String reference, String libelle, boolean derivee) {
    }
}
