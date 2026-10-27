package ci.smartex.offres.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;

/**
 * Le client Anthropic, construit a la premiere extraction et pas avant.
 *
 * <p>La cle vient de la variable d'environnement ANTHROPIC_API_KEY, lue par le
 * SDK : elle ne traverse ni le code ni la base.
 *
 * <p>Le bean est paresseux a dessein. Une cle absente ou invalide ne doit pas
 * empecher l'application de demarrer : sans elle, on perd l'extraction
 * automatique du DAO, pas la possibilite de se connecter, de saisir des prix
 * et de sortir un dossier. Faire echouer le demarrage pour cela transformerait
 * une gene en panne totale.
 *
 * <p>Le delai est large parce qu'un DAO de cent pages demande une longue
 * lecture ; la tache tourne de toute facon en fond, personne n'attend devant
 * son ecran.
 */
@Configuration
public class ConfigurationIa {

    @Bean
    @Lazy
    public AnthropicClient clientAnthropic() {
        return AnthropicOkHttpClient.builder()
                .fromEnv()
                .timeout(Duration.ofMinutes(10))
                .build();
    }
}
