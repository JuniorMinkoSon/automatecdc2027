package ci.smartex.offres.extraction;

import ci.smartex.offres.config.ProprietesApp;
import com.anthropic.client.AnthropicClient;
import org.springframework.beans.factory.ObjectProvider;
import com.anthropic.core.JsonSchemaLocalValidation;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lit un DAO et en rend les champs, sous une forme imposee.
 *
 * <p>Le modele ne decide de rien : il lit et transcrit. Ce qu'il rend part en
 * base avec le statut « A verifier », passe sous les yeux d'un humain, et
 * seules des donnees relues alimentent ensuite les modeles Jasper. L'ecart
 * entre « l'IA extrait » et « l'IA redige » est toute la difference entre une
 * offre qu'on peut deposer et une offre qu'on ne peut pas defendre.
 *
 * <p>L'IA ne recoit que le texte du DAO. Aucune piece d'identite, aucun CV,
 * aucune donnee nominative du personnel ne part vers un service externe.
 */
@Component
public class ExtracteurDao {

    private static final Logger LOG = LoggerFactory.getLogger(ExtracteurDao.class);

    /**
     * Un DAO fait couramment cent pages. Au-dela, le texte est tronque par
     * sections : le decoupage amont garde les sections II, III et IV, qui
     * portent les donnees particulieres, les criteres et les formulaires.
     */
    private static final int CARACTERES_MAX = 600_000;

    private static final String CONSIGNE = """
            Tu lis un dossier d'appel d'offres (DAO) de travaux publics en Cote d'Ivoire
            et tu en transcris les donnees. Tu ne rediges rien et tu ne conseilles rien.

            Regles, dans cet ordre de priorite :

            1. Ne jamais inventer. Si le DAO ne dit pas une chose, le champ reste une
               chaine vide. Un emplacement laisse en blanc dans le DAO est une
               information en soi : signale-le dans pointsDAttention.
            2. Recopier, pas reformuler. Les intitules de marche et de lots repartent
               tels quels dans les pieces officielles ; une reformulation, meme
               meilleure, rend l'offre non conforme au bordereau.
            3. Citer la clause. Chaque exigence porte la reference du DAO d'ou elle
               vient, pour que le soumissionnaire puisse la retrouver.
            4. Signaler les contradictions plutot que les arbitrer. Un DAO qui exige des
               prix TTC tout en declarant la TVA non prise en compte se contredit :
               c'est un point d'attention, pas a toi de choisir.

            Les montants sont en francs CFA, sans decimales. Les durees sont en jours
            pour la validite de l'offre, en mois pour les delais d'execution.
            """;

    private final ObjectProvider<AnthropicClient> client;
    private final ProprietesApp proprietes;

    public ExtracteurDao(ObjectProvider<AnthropicClient> client, ProprietesApp proprietes) {
        this.client = client;
        this.proprietes = proprietes;
    }

    /**
     * Resout le client au moment de l'appel. Une cle absente se traduit alors
     * par une extraction impossible et un DAO a saisir a la main, et non par
     * une application qui refuse de demarrer.
     */
    private AnthropicClient client() {
        try {
            return client.getObject();
        } catch (RuntimeException erreur) {
            throw new ExtractionImpossibleException(
                    "Le service d'extraction n'est pas configure (cle d'API absente ou "
                            + "invalide). Saisissez les champs a la main.", erreur);
        }
    }

    public ExtractionDao extraire(String texteDao) {
        if (texteDao == null || texteDao.isBlank()) {
            throw new ExtractionImpossibleException(
                    "Le DAO ne contient aucun texte lisible, meme apres OCR.");
        }

        String texte = texteDao.length() > CARACTERES_MAX
                ? texteDao.substring(0, CARACTERES_MAX)
                : texteDao;
        if (texte.length() < texteDao.length()) {
            LOG.warn("DAO tronque a {} caracteres sur {} pour l'extraction.",
                    CARACTERES_MAX, texteDao.length());
        }

        // Le schema vient de la classe ExtractionDao : le modele ne peut pas
        // rendre un champ en trop, en oublier un, ni se tromper de type.
        // La validation locale rattrape ce qui passerait quand meme.
        MessageCreateParams.Builder base = MessageCreateParams.builder()
                .model(proprietes.getIa().getModele())
                .maxTokens(proprietes.getIa().getMaxTokens())
                .system(CONSIGNE);

        var params = base
                .outputConfig(ExtractionDao.class, JsonSchemaLocalValidation.YES)
                .addUserMessage("Voici le texte du DAO a transcrire.\n\n" + texte)
                .build();

        StructuredMessage<ExtractionDao> reponse;
        try {
            reponse = client().messages().create(params);
        } catch (RuntimeException erreur) {
            throw new ExtractionImpossibleException(
                    "L'analyse du DAO a echoue : " + erreur.getMessage(), erreur);
        }

        // Un refus arrive en HTTP 200 : sans ce test, on lirait un contenu vide
        // comme une extraction reussie et on ecrirait des champs blancs en base.
        if (reponse.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            String categorie = reponse.stopDetails()
                    .map(details -> details.category().toString())
                    .orElse("non precisee");
            throw new ExtractionImpossibleException(
                    "L'analyse a ete refusee (" + categorie + "). Saisissez les champs a la main.");
        }

        if (reponse.stopReason().filter(StopReason.MAX_TOKENS::equals).isPresent()) {
            throw new ExtractionImpossibleException(
                    "L'extraction a ete coupee avant la fin. Le DAO est trop volumineux "
                            + "pour une seule passe.");
        }

        return reponse.content().stream()
                .flatMap(bloc -> bloc.text().stream())
                .map(bloc -> bloc.text())
                .findFirst()
                .orElseThrow(() -> new ExtractionImpossibleException(
                        "La reponse ne contient aucune extraction exploitable."));
    }
}
