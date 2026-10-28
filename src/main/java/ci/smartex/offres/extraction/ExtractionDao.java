package ci.smartex.offres.extraction;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * La forme imposee a l'extraction d'un DAO.
 *
 * <p>Le SDK derive le schema JSON de cette classe et le fait respecter par le
 * modele : la reponse ne peut pas arriver avec un champ en trop, un champ
 * manquant ou un type imprevu.
 *
 * <p>Tous les champs scalaires sont du texte, y compris les nombres et les
 * dates. Ce n'est pas une facilite : c'est ce qui permet au modele de rendre
 * une chaine vide quand le DAO ne dit rien, au lieu d'inventer un zero. La
 * maquette d'analyse affiche « Vide dans le DAO » pour la date limite du vrai
 * DAO de reference ; c'est ce cas-la qu'il faut pouvoir representer. La
 * conversion et la validation se font ensuite en Java, ou une valeur
 * illisible devient une alerte et non une donnee fausse.
 */
public class ExtractionDao {

    @JsonPropertyDescription("Numero de l'appel d'offres tel qu'il figure au DAO. "
            + "Chaine vide s'il n'y figure pas.")
    public String numeroAo = "";

    @JsonPropertyDescription("Intitule complet du marche, recopie mot pour mot.")
    public String intitule = "";

    @JsonPropertyDescription("Autorite contractante (IC 1.1). Chaine vide si absente.")
    public String autoriteContractante = "";

    @JsonPropertyDescription("Maitre d'ouvrage. Chaine vide si absent.")
    public String maitreOuvrage = "";

    @JsonPropertyDescription("Maitre d'oeuvre. Chaine vide si absent.")
    public String maitreOeuvre = "";

    @JsonPropertyDescription("Destinataire a qui la soumission est adressee. "
            + "Chaine vide si absent.")
    public String destinataire = "";

    @JsonPropertyDescription("Source de financement. Chaine vide si absente.")
    public String financement = "";

    @JsonPropertyDescription("Duree de validite de l'offre en jours, en chiffres seulement "
            + "(exemple : 120). Chaine vide si le DAO ne la fixe pas.")
    public String validiteOffreJours = "";

    @JsonPropertyDescription("Regime de prix tel que le DAO le formule "
            + "(exemple : TTC, fermes et non revisables).")
    public String regimePrix = "";

    @JsonPropertyDescription("oui si la TVA s'applique, non si le DAO la declare non prise "
            + "en compte, chaine vide si le DAO ne tranche pas.")
    public String tvaApplicable = "";

    @JsonPropertyDescription("Date et heure limites de depot au format AAAA-MM-JJTHH:MM. "
            + "Chaine vide si le DAO laisse l'emplacement en blanc.")
    public String dateLimiteDepot = "";

    @JsonPropertyDescription("Montant du timbre fiscal en francs CFA, chiffres seulement. "
            + "Chaine vide si absent.")
    public String timbreFiscal = "";

    @JsonPropertyDescription("Les lots du marche. Liste vide si le marche n'est pas alloti.")
    public List<LotExtrait> lots = List.of();

    @JsonPropertyDescription("Les criteres que le soumissionnaire doit satisfaire : "
            + "personnel, materiel, chiffre d'affaires, experience, pieces exigees.")
    public List<ExigenceExtraite> exigences = List.of();

    @JsonPropertyDescription("Ce que le DAO laisse en blanc, se contredit, ou ne permet pas "
            + "de trancher. Une phrase par point. Liste vide si tout est clair.")
    public List<String> pointsDAttention = List.of();

    public static class LotExtrait {

        @JsonPropertyDescription("Numero du lot, chiffres seulement.")
        public String numero = "";

        @JsonPropertyDescription("Intitule du lot.")
        public String intitule = "";

        @JsonPropertyDescription("Localite du lot. Chaine vide si absente.")
        public String localite = "";

        @JsonPropertyDescription("Delai d'execution en mois, chiffres seulement. "
                + "Chaine vide si le DAO ne le fixe pas pour ce lot.")
        public String delaiMois = "";

        @JsonPropertyDescription("Montant de la garantie d'offre pour ce lot, en francs CFA, "
                + "chiffres seulement. Chaine vide si absent.")
        public String montantGarantie = "";
    }

    public static class ExigenceExtraite {

        @JsonPropertyDescription("Nature du critere : PERSONNEL, MATERIEL, CHIFFRE_AFFAIRES, "
                + "EXPERIENCE, PIECE ou AUTRE.")
        public String type = "AUTRE";

        @JsonPropertyDescription("Le critere en quelques mots, tel que le DAO le nomme "
                + "(exemple : Conducteur des travaux).")
        public String libelle = "";

        @JsonPropertyDescription("Le detail du critere, recopie du DAO "
                + "(exemple : BTS batiment, 5 ans d'experience).")
        public String valeur = "";

        @JsonPropertyDescription("Quantite minimale exigee, chiffres seulement. "
                + "Chaine vide si le DAO n'en fixe pas.")
        public String quantiteMin = "";

        @JsonPropertyDescription("Annees d'experience minimales, chiffres seulement. "
                + "Chaine vide si le DAO n'en fixe pas.")
        public String anneesMin = "";

        @JsonPropertyDescription("Numero du lot concerne, chiffres seulement. "
                + "Chaine vide si le critere vaut pour tous les lots.")
        public String numeroLot = "";

        @JsonPropertyDescription("oui si le non-respect elimine l'offre, non sinon.")
        public String eliminatoire = "non";

        @JsonPropertyDescription("La clause du DAO qui porte ce critere "
                + "(exemple : IC 13.2, Section III 5.1).")
        public String sourceDao = "";
    }
}
