package ci.smartex.offres.rendu;

/**
 * Les sections que le code des marches fige.
 *
 * <p>Instructions aux candidats, Cahier des clauses administratives generales,
 * formulaires types : ce sont des textes reglementaires, identiques d'un marche
 * a l'autre. Les laisser modifier serait rendre service a personne - une
 * collectivite qui y glisserait une clause produirait un dossier attaquable, et
 * c'est elle qui en repondrait devant l'organe de regulation.
 *
 * <p>Ils sont donc livres avec la plateforme, sous Git, et poses verrouilles
 * dans chaque dossier. Ce qui se redige, ce sont les donnees particulieres, les
 * criteres, le cahier technique et les clauses particulieres : c'est la que
 * le temps se perd aujourd'hui, et c'est la seule chose que l'autorite
 * contractante doit avoir a ecrire.
 *
 * <p>Les textes ci-dessous sont des renvois a la reglementation, pas sa
 * transcription integrale. Recopier ici plusieurs dizaines de pages de code
 * exposerait a les laisser vieillir sans s'en apercevoir, ce qui serait pire
 * que de ne pas les recopier du tout : un dossier qui cite un texte abroge est
 * plus dangereux qu'un dossier qui renvoie au texte en vigueur. Le contenu
 * integral se depose comme modele, par la fiche des modeles de documents.
 */
public final class TextesReglementaires {

    private TextesReglementaires() {
    }

    public static String texte(String codeSection) {
        return switch (codeSection) {
            case "IC" -> INSTRUCTIONS_AUX_CANDIDATS;
            case "FORMULAIRES" -> FORMULAIRES_DE_SOUMISSION;
            case "CCAG" -> CLAUSES_ADMINISTRATIVES_GENERALES;
            case "FORMULAIRES_MARCHE" -> FORMULAIRES_DU_MARCHE;
            default -> null;
        };
    }

    /** Vrai pour les sections dont le texte ne se modifie pas. */
    public static boolean estFigee(String codeSection) {
        return texte(codeSection) != null;
    }

    private static final String INSTRUCTIONS_AUX_CANDIDATS = """
            Les présentes instructions aux candidats sont celles du dossier type \
            d'appel d'offres pour la passation des marchés de travaux, dans leur \
            rédaction en vigueur. Elles ne sont pas modifiables.

            Elles couvrent notamment : l'objet du marché et l'origine des fonds ; \
            la sanction des fautes commises dans la passation et l'exécution des \
            marchés ; les conditions à remplir pour participer à la procédure et \
            les situations de conflit d'intérêts ; le contenu du dossier d'appel \
            d'offres, les demandes d'éclaircissement et la visite du site ; la \
            préparation des offres, leur langue, leurs documents constitutifs, \
            les variantes, les prix et rabais, la monnaie, la période de validité \
            et le cautionnement provisoire ; la forme, la signature, le cachetage \
            et le marquage des offres ; la date limite de remise, les offres hors \
            délai, le retrait, la substitution et la modification ; l'ouverture \
            des plis ; la confidentialité, l'évaluation et la comparaison des \
            offres, le traitement des divergences, réserves et omissions, la \
            correction des erreurs arithmétiques ; les critères d'attribution, la \
            notification, la signature du marché, le cautionnement définitif et \
            les recours.

            Toute adaptation à ce marché doit figurer dans les données \
            particulières de l'appel d'offres, et non ici. Modifier les \
            instructions elles-mêmes exposerait le dossier à un recours.

            Le texte intégral en vigueur est tenu à la disposition des candidats \
            avec le dossier. Pour l'y joindre, déposez-le comme modèle de \
            document : il sera repris tel quel, avec sa mise en page.
            """;

    private static final String FORMULAIRES_DE_SOUMISSION = """
            Les formulaires que les candidats doivent utiliser sont ceux du \
            dossier type, et doivent être repris tels quels.

            Ils comprennent notamment : la lettre de soumission de l'offre ; le \
            bordereau des prix unitaires et le détail quantitatif et estimatif ; \
            le formulaire de renseignements sur le candidat et, en cas de \
            groupement, sur chacun de ses membres ; la déclaration sur l'honneur ; \
            les antécédents de marchés non exécutés ; les formulaires relatifs au \
            personnel clé et au matériel ; le modèle de cautionnement provisoire.

            Un candidat qui remplace un de ces formulaires par un document de son \
            choix présente une offre non conforme, même si le contenu est \
            équivalent.
            """;

    private static final String CLAUSES_ADMINISTRATIVES_GENERALES = """
            Le cahier des clauses administratives générales applicable aux \
            marchés publics de travaux s'applique au présent marché dans sa \
            rédaction en vigueur. Il n'est pas modifiable.

            Il régit notamment : les définitions et l'interprétation du marché ; \
            les obligations générales du titulaire et du maître d'ouvrage ; la \
            sous-traitance ; les délais d'exécution, leur prolongation et les \
            pénalités de retard ; les prix, leur révision et leur actualisation ; \
            les modalités de règlement, les avances, les acomptes et le solde ; \
            la réception des travaux et les garanties ; les assurances ; la \
            résiliation ; le règlement des différends.

            Les dérogations au cahier des clauses administratives générales \
            doivent figurer, article par article, dans le cahier des clauses \
            administratives particulières, qui en dresse la liste limitative. \
            Une dérogation qui n'y figure pas est réputée non écrite.
            """;

    private static final String FORMULAIRES_DU_MARCHE = """
            Les formulaires du marché sont ceux du dossier type et sont complétés \
            par l'attributaire après la décision d'attribution.

            Ils comprennent notamment : la lettre de notification de \
            l'attribution ; l'acte d'engagement ; le modèle de cautionnement \
            définitif ; le modèle de garantie de restitution d'avance.

            Les notes qui accompagnent ces formulaires sont conservées dans le \
            dossier remis aux candidats : elles contiennent les instructions qui \
            leur sont destinées.
            """;
}
