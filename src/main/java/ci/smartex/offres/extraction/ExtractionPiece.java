package ci.smartex.offres.extraction;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * La forme imposee a la lecture d'une piece administrative.
 *
 * <p>Comme pour le DAO, tout est du texte : une date illisible rend une chaine
 * vide, pas une date inventee. Une piece dont la date d'expiration est fausse
 * est pire qu'une piece sans date, parce qu'elle passe les controles.
 */
public class ExtractionPiece {

    @JsonPropertyDescription("Type de la piece : RCCM, DFE, ATTESTATION_PME, QUITUS_ARCOP, "
            + "ATTESTATION_BANCAIRE, ATTESTATION_FISCALE, CNPS, CAUTIONNEMENT ou AUTRE.")
    public String type = "AUTRE";

    @JsonPropertyDescription("Intitule de la piece tel qu'il figure sur le document.")
    public String libelle = "";

    @JsonPropertyDescription("Numero de la piece. Chaine vide s'il n'y en a pas.")
    public String numero = "";

    @JsonPropertyDescription("Date d'emission au format AAAA-MM-JJ. Chaine vide si illisible.")
    public String dateEmission = "";

    @JsonPropertyDescription("Date d'expiration au format AAAA-MM-JJ. Chaine vide si la piece "
            + "n'en porte pas ou si elle est illisible. Ne jamais la calculer ni la deduire.")
    public String dateExpiration = "";

    @JsonPropertyDescription("Montant porte par la piece en francs CFA, chiffres seulement. "
            + "Ne concerne que le cautionnement. Chaine vide sinon ou si illisible.")
    public String montant = "";

    @JsonPropertyDescription("Ce qui est douteux dans la lecture : tampon illisible, page "
            + "coupee, date ambigue. Chaine vide si la lecture est sure.")
    public String reserve = "";
}
