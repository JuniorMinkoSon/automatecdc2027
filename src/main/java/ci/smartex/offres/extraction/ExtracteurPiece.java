package ci.smartex.offres.extraction;

import ci.smartex.offres.config.ProprietesApp;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonSchemaLocalValidation;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import org.springframework.stereotype.Component;

/**
 * Lit une piece du coffre : son type, son numero et surtout ses dates.
 *
 * <p>C'est la date d'expiration qui compte : c'est elle que la veille
 * quotidienne surveille et c'est elle qui fait echouer un controle bloquant
 * quand la piece est perimee a la date limite de depot.
 *
 * <p>L'utilisateur confirme en un clic. Tant qu'il ne l'a pas fait, la piece
 * n'entre dans aucun dossier : la machine propose une lecture, elle ne la
 * valide pas elle-meme.
 */
@Component
public class ExtracteurPiece {

    private static final int CARACTERES_MAX = 40_000;

    private static final String CONSIGNE = """
            Tu lis une piece administrative ivoirienne (registre de commerce, declaration
            fiscale d'existence, attestation PME, quitus ARCOP, attestation bancaire,
            cautionnement) et tu en transcris les donnees.

            Deux regles :

            1. Ne jamais deduire une date. Si une attestation ne porte pas de date
               d'expiration, le champ reste vide - ne la calcule pas a partir de la date
               d'emission, meme si l'usage veut qu'elle vaille trois mois. Une date
               inventee est pire qu'une date absente : elle passe les controles.
            2. Dire ce qui est douteux. Un tampon illisible, une page coupee, un chiffre
               ambigu : ecris-le dans reserve plutot que de choisir.
            """;

    private final AnthropicClient client;
    private final ProprietesApp proprietes;

    public ExtracteurPiece(AnthropicClient client, ProprietesApp proprietes) {
        this.client = client;
        this.proprietes = proprietes;
    }

    public ExtractionPiece extraire(String textePiece) {
        if (textePiece == null || textePiece.isBlank()) {
            throw new ExtractionImpossibleException(
                    "Cette piece ne contient aucun texte lisible, meme apres OCR.");
        }

        String texte = textePiece.length() > CARACTERES_MAX
                ? textePiece.substring(0, CARACTERES_MAX)
                : textePiece;

        var params = MessageCreateParams.builder()
                .model(proprietes.getIa().getModele())
                .maxTokens(4000L)
                .system(CONSIGNE)
                .outputConfig(ExtractionPiece.class, JsonSchemaLocalValidation.YES)
                .addUserMessage("Voici le texte de la piece.\n\n" + texte)
                .build();

        var reponse = client.messages().create(params);

        if (reponse.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            throw new ExtractionImpossibleException(
                    "La lecture de cette piece a ete refusee. Saisissez les dates a la main.");
        }

        return reponse.content().stream()
                .flatMap(bloc -> bloc.text().stream())
                .map(bloc -> bloc.text())
                .findFirst()
                .orElseThrow(() -> new ExtractionImpossibleException(
                        "La lecture n'a rien rendu d'exploitable."));
    }
}
