package ci.smartex.offres.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Le client Anthropic, construit une fois.
 *
 * <p>La cle vient de la variable d'environnement ANTHROPIC_API_KEY, lue par le
 * SDK : elle ne traverse ni le code ni la base.
 *
 * <p>Le delai est large parce qu'un DAO de cent pages demande une longue
 * lecture ; la tache tourne de toute facon en fond, personne n'attend devant
 * son ecran.
 */
@Configuration
public class ConfigurationIa {

    @Bean
    public AnthropicClient clientAnthropic() {
        return AnthropicOkHttpClient.builder()
                .fromEnv()
                .timeout(Duration.ofMinutes(10))
                .build();
    }
}
