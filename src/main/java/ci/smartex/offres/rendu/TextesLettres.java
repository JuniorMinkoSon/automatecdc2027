package ci.smartex.offres.rendu;

import ci.smartex.offres.util.MontantEnLettres;

import java.util.Map;

/**
 * Le corps des lettres types, en un seul endroit.
 *
 * <p>Les formulations reprennent celles des dossiers de reponse deja deposes,
 * parce que ce sont elles que les commissions ont l'habitude de lire et qu'une
 * reformulation, meme meilleure, se remarque. Ce qui change, c'est que plus
 * aucune identite n'y figure : le nom de l'entreprise, le signataire, le
 * numero d'appel d'offres et l'objet du marche sont des variables, lues une
 * fois en base et reportees partout.
 *
 * <p>C'est precisement ce que la saisie a la main ne garantissait pas. Dans le
 * dossier de reference, l'objet du marche est recopie a l'identique dans sept
 * pieces : une faute de frappe dans l'une d'elles suffit a faire douter de
 * l'ensemble.
 *
 * <p>Les durees s'ecrivent en lettres suivies des chiffres entre parentheses,
 * comme dans les pieces officielles : c'est la forme qui fait foi en cas de
 * divergence.
 *
 * <p>Ces textes sont sous Git, donc sous revue et sous historique, et une
 * entreprise ne peut pas les modifier. Un formulaire du DAO ou quelqu'un
 * aurait glisse une reserve n'est plus le formulaire du DAO.
 */
public final class TextesLettres {

    private TextesLettres() {
    }

    /** Les pieces que ce modele sait produire, dans l'ordre du sommaire. */
    public static final String DELAI_EXECUTION = "DELAI_EXECUTION";
    public static final String VALIDITE_OFFRE = "VALIDITE_OFFRE";
    public static final String VALIDITE_GARANTIE = "VALIDITE_GARANTIE";
    public static final String POUVOIRS = "POUVOIRS";
    public static final String ENGAGEMENT_DELAI = "ENGAGEMENT_DELAI";
    public static final String CONFLIT_INTERETS = "CONFLIT_INTERETS";

    public static String titre(String codeModele) {
        return switch (codeModele) {
            case DELAI_EXECUTION -> "Délai d'exécution des travaux";
            case VALIDITE_OFFRE -> "Période de validité de l'offre";
            case VALIDITE_GARANTIE -> "Période de validité de la garantie de soumission";
            case POUVOIRS -> "Pouvoirs habilitant le soumissionnaire";
            case ENGAGEMENT_DELAI -> "Acte d'engagement pour le respect du délai "
                    + "de livraison des travaux";
            case CONFLIT_INTERETS -> "Conflit d'intérêts";
            default -> codeModele;
        };
    }

    /**
     * Le corps de la lettre.
     *
     * @param donnees les valeurs du dossier : entreprise, signataire, appel
     *                d'offres, durees. Aucune n'est ecrite ici en dur.
     */
    public static String corps(String codeModele, Donnees donnees) {
        return switch (codeModele) {
            case DELAI_EXECUTION -> delaiExecution(donnees);
            case VALIDITE_OFFRE -> validiteOffre(donnees);
            case VALIDITE_GARANTIE -> validiteGarantie(donnees);
            case POUVOIRS -> pouvoirs(donnees);
            case ENGAGEMENT_DELAI -> engagementDelai(donnees);
            case CONFLIT_INTERETS -> conflitInterets(donnees);
            default -> "";
        };
    }

    // ------------------------------------------------------------------ textes

    private static String delaiExecution(Donnees d) {
        if (d.delaiMois() == null) {
            return aCompleter("le délai d'exécution");
        }
        return "Je soussigné %s, %s de l'entreprise %s, déclare que le délai d'exécution "
                .formatted(d.signataire(), d.fonction(), d.entreprise())
                + "des travaux dans le cadre de " + referenceMarche(d)
                + " est de %s (%02d) mois%s."
                        .formatted(MontantEnLettres.nombre(d.delaiMois()),
                                d.delaiMois(),
                                d.plusieursLots() ? " pour chaque lot" : "");
    }

    private static String validiteOffre(Donnees d) {
        if (d.validiteOffreJours() == null) {
            return aCompleter("la durée de validité de l'offre");
        }
        return "Je soussigné %s, %s de l'entreprise %s, déclare que la durée de validité "
                .formatted(d.signataire(), d.fonction(), d.entreprise())
                + "de notre offre dans le cadre de " + referenceMarche(d)
                + " est de %s (%d) jours."
                        .formatted(MontantEnLettres.nombre(d.validiteOffreJours()),
                                d.validiteOffreJours());
    }

    private static String validiteGarantie(Donnees d) {
        if (d.validiteGarantieJours() == null) {
            return aCompleter("la durée de validité de la garantie de soumission");
        }
        return "Je soussigné %s, %s de l'entreprise %s, déclare que la durée de validité "
                .formatted(d.signataire(), d.fonction(), d.entreprise())
                + "de la garantie de soumission dans le cadre de " + referenceMarche(d)
                + " est de %s (%d) jours."
                        .formatted(MontantEnLettres.nombre(d.validiteGarantieJours()),
                                d.validiteGarantieJours());
    }

    private static String pouvoirs(Donnees d) {
        String adresse = d.adresse() == null || d.adresse().isBlank()
                ? "" : ", " + d.adresse();
        return "Je soussigné %s, %s de l'entreprise %s, déclare avoir pouvoir pour signer "
                .formatted(d.signataire(), d.fonction(), d.entreprise())
                + "tout document concernant l'entreprise %s%s dans le cadre de "
                        .formatted(d.entreprise(), adresse)
                + referenceMarche(d) + ".";
    }

    private static String engagementDelai(Donnees d) {
        if (d.delaiMois() == null) {
            return aCompleter("le délai de livraison");
        }
        return """
               Je soussigné %s, %s, agissant pour le compte de l'entreprise %s.

               Après avoir pris connaissance du Dossier d'Appel d'Offres (DAO) et des \
               documents qui y sont mentionnés, je m'ENGAGE à respecter le délai de \
               livraison de %s (%02d) mois.

               Au cas où l'entreprise %s est déclarée attributaire d'un marché, le \
               non-respect de ce délai de livraison entraînera la résiliation dudit \
               marché pour faute.

               En foi de quoi je délivre le présent acte d'engagement pour servir et \
               valoir ce que de droit.\
               """.formatted(d.signataire(), d.fonction(), d.entreprise(),
                        MontantEnLettres.nombre(d.delaiMois()), d.delaiMois(),
                        d.entreprise());
    }

    private static String conflitInterets(Donnees d) {
        String situation = d.adresse() == null || d.adresse().isBlank()
                ? "" : ", située à " + d.adresse() + ",";
        // Le dossier de reference ecrit « Je soussigné ... n'a pas », qui est
        // une faute d'accord. Elle n'est pas reprise : le cahier des charges
        // demande explicitement que les fautes de recopie disparaissent.
        return "Je soussigné %s, %s de l'entreprise %s%s déclare n'avoir aucun conflit "
                .formatted(d.signataire(), d.fonction(), d.entreprise(), situation)
                + "d'intérêts dans le cadre de " + referenceMarche(d) + ".\n\n"
                + "En foi de quoi je délivre le présent acte d'engagement pour servir "
                + "et valoir ce que de droit.";
    }

    /**
     * La reference du marche, ecrite une seule fois et reutilisee par toutes
     * les lettres. C'est le fragment le plus recopie d'un dossier, donc celui
     * qui concentre les fautes quand il est tape sept fois.
     */
    private static String referenceMarche(Donnees d) {
        String numero = d.numeroAo() == null || d.numeroAo().isBlank()
                ? "l'appel d'offres"
                : "l'appel d'offres n° " + d.numeroAo();
        return numero + " relatif aux " + d.objetMarche();
    }

    private static String aCompleter(String quoi) {
        return "[À COMPLÉTER : " + quoi + " n'est pas renseigné dans le dossier. "
                + "Cette pièce ne doit pas être déposée en l'état.]";
    }

    /**
     * Ce dont une lettre type a besoin. Tout vient de la base ou du formulaire,
     * rien du code.
     *
     * @param plusieursLots vrai si le marche est alloti : la formule devient
     *                      « pour chaque lot », comme dans les dossiers deposes
     */
    public record Donnees(String entreprise,
                          String adresse,
                          String signataire,
                          String fonction,
                          String numeroAo,
                          String objetMarche,
                          Integer delaiMois,
                          Integer validiteOffreJours,
                          Integer validiteGarantieJours,
                          boolean plusieursLots) {

        /** Construit les donnees a partir des parametres deja assembles pour Jasper. */
        public static Donnees depuisParametres(Map<String, Object> parametres,
                                               Integer delaiMois,
                                               Integer validiteOffreJours,
                                               Integer validiteGarantieJours,
                                               boolean plusieursLots) {
            return new Donnees(
                    texte(parametres, "ENTREPRISE_RAISON_SOCIALE"),
                    texte(parametres, "ENTREPRISE_ADRESSE"),
                    texte(parametres, "SIGNATAIRE_NOM"),
                    texte(parametres, "SIGNATAIRE_FONCTION"),
                    texte(parametres, "AO_NUMERO"),
                    texte(parametres, "AO_INTITULE"),
                    delaiMois,
                    validiteOffreJours,
                    validiteGarantieJours,
                    plusieursLots);
        }

        private static String texte(Map<String, Object> parametres, String cle) {
            Object valeur = parametres.get(cle);
            return valeur == null ? "" : valeur.toString();
        }
    }
}
