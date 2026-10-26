package ci.smartex.offres.util;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Conversion d'un montant en toutes lettres, et d'une date en toutes lettres.
 *
 * <p>Une lettre de soumission dont le montant en chiffres et le montant en
 * lettres divergent est rejetee par la COJO. Ces deux ecritures viennent donc
 * du meme nombre, par ce seul chemin : aucune saisie libre nulle part.
 *
 * <p>Orthographe retenue : la tradition administrative, pas la reforme de 1990.
 * Les groupes sont separes par des espaces, les unites liees par des traits
 * d'union, et {@code et} s'ecrit sans trait d'union. C'est ce qu'on lit dans
 * les DAO et dans les offres deja deposees.
 *
 * <p>Accords de {@code cent} et {@code vingt} : le {@code s} du pluriel
 * n'apparait que si le groupe termine le nombre ou precede un nom
 * ({@code millions}, {@code milliards}, {@code francs}). Devant {@code mille},
 * qui est un adjectif numeral invariable, il disparait : on ecrit
 * {@code deux cents millions} mais {@code deux cent mille}.
 */
public final class MontantEnLettres {

    private static final String[] UNITES = {
            "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
            "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize",
            "dix-sept", "dix-huit", "dix-neuf"
    };

    private static final String[] DIZAINES = {
            "", "", "vingt", "trente", "quarante", "cinquante", "soixante"
    };

    private MontantEnLettres() {
    }

    /**
     * Montant complet tel qu'il doit figurer sur une piece : premiere lettre en
     * capitale, devise ecrite en toutes lettres.
     *
     * <p>Exemple : {@code 18 639 665} donne
     * « Dix-huit millions six cent trente-neuf mille six cent soixante-cinq francs CFA ».
     */
    public static String montant(long valeur) {
        return montant(valeur, null);
    }

    /**
     * Idem, avec une mention de regime fiscal ajoutee a la fin, par exemple
     * « toutes taxes comprises » ou « hors taxes ».
     */
    public static String montant(long valeur, String mention) {
        // Zero et un restent au singulier : « zéro franc », « un franc ».
        String lettres = nombre(valeur) + (Math.abs(valeur) < 2 ? " franc CFA" : " francs CFA");
        if (mention != null && !mention.isBlank()) {
            lettres = lettres + " " + mention.trim();
        }
        return majusculeInitiale(lettres);
    }

    /**
     * Le montant en chiffres, groupe par milliers : « 850 000 F CFA ».
     *
     * <p>C'est cette forme qui va dans les messages d'erreur a l'ecran. Les
     * lettres sont faites pour une piece officielle, pas pour une alerte qu'on
     * lit en diagonale.
     */
    public static String chiffres(long valeur) {
        java.text.NumberFormat format = java.text.NumberFormat.getIntegerInstance(Locale.FRENCH);
        return format.format(valeur).replace(' ', ' ').replace(' ', ' ') + " F CFA";
    }

    /**
     * Le nombre seul, en minuscules et sans devise.
     */
    public static String nombre(long valeur) {
        if (valeur < 0) {
            return "moins " + nombre(-valeur);
        }
        if (valeur == 0) {
            return UNITES[0];
        }

        long milliards = valeur / 1_000_000_000L;
        long millions = (valeur / 1_000_000L) % 1000L;
        long milliers = (valeur / 1000L) % 1000L;
        long unites = valeur % 1000L;

        StringBuilder sortie = new StringBuilder();

        if (milliards > 0) {
            // Un groupe suivi d'un nom garde son pluriel : « deux cents milliards ».
            ajouter(sortie, groupe(milliards, true) + (milliards > 1 ? " milliards" : " milliard"));
        }
        if (millions > 0) {
            ajouter(sortie, groupe(millions, true) + (millions > 1 ? " millions" : " million"));
        }
        if (milliers > 0) {
            // « mille » est invariable et n'est pas un nom : il annule le pluriel
            // du groupe qui le precede, et ne prend jamais d'article.
            ajouter(sortie, milliers == 1 ? "mille" : groupe(milliers, false) + " mille");
        }
        if (unites > 0) {
            ajouter(sortie, groupe(unites, true));
        }

        return sortie.toString();
    }

    /**
     * Date en toutes lettres, pour les lettres types : « quatorze août deux mille vingt-six ».
     */
    public static String date(LocalDate date) {
        String jour = date.getDayOfMonth() == 1 ? "premier" : nombre(date.getDayOfMonth());
        String mois = date.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH);
        return jour + " " + mois + " " + nombre(date.getYear());
    }

    // ------------------------------------------------------------------ interne

    /**
     * Un groupe de trois chiffres (1 a 999).
     *
     * @param pluralisable vrai si le groupe termine le nombre ou precede un nom,
     *                     ce qui autorise le {@code s} de {@code cents} et de
     *                     {@code quatre-vingts}.
     */
    private static String groupe(long valeur, boolean pluralisable) {
        long centaines = valeur / 100L;
        long reste = valeur % 100L;

        StringBuilder sortie = new StringBuilder();

        if (centaines == 1) {
            sortie.append("cent");
        } else if (centaines > 1) {
            sortie.append(UNITES[(int) centaines]).append(" cent");
            if (reste == 0 && pluralisable) {
                sortie.append('s');
            }
        }

        if (reste > 0) {
            ajouter(sortie, deuxChiffres(reste, pluralisable));
        }

        return sortie.toString();
    }

    private static String deuxChiffres(long valeur, boolean pluralisable) {
        if (valeur < 20) {
            return UNITES[(int) valeur];
        }

        int dizaine = (int) (valeur / 10L);
        int unite = (int) (valeur % 10L);

        return switch (dizaine) {
            // 20 a 69 : « vingt et un », « trente-deux ».
            case 2, 3, 4, 5, 6 -> switch (unite) {
                case 0 -> DIZAINES[dizaine];
                case 1 -> DIZAINES[dizaine] + " et un";
                default -> DIZAINES[dizaine] + "-" + UNITES[unite];
            };
            // 70 a 79 se comptent sur soixante : « soixante et onze », « soixante-douze ».
            case 7 -> unite == 1
                    ? "soixante et onze"
                    : "soixante-" + UNITES[10 + unite];
            // 80 : pluriel seulement s'il termine le nombre ou precede un nom.
            case 8 -> unite == 0
                    ? (pluralisable ? "quatre-vingts" : "quatre-vingt")
                    : "quatre-vingt-" + UNITES[unite];
            // 90 a 99 : « quatre-vingt-dix », « quatre-vingt-onze », sans « et ».
            case 9 -> "quatre-vingt-" + UNITES[10 + unite];
            default -> throw new IllegalStateException("Dizaine hors plage : " + dizaine);
        };
    }

    private static void ajouter(StringBuilder sortie, String morceau) {
        if (!sortie.isEmpty()) {
            sortie.append(' ');
        }
        sortie.append(morceau);
    }

    private static String majusculeInitiale(String texte) {
        if (texte.isEmpty()) {
            return texte;
        }
        return Character.toUpperCase(texte.charAt(0)) + texte.substring(1);
    }
}
