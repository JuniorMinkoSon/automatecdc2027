package ci.smartex.offres.config;

import ci.smartex.offres.securite.FiltreContexteOrganisation;
import ci.smartex.offres.securite.FournisseurAuthentification;
import ci.smartex.offres.securite.SourceDetailsAvecCode;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Les droits sont verifies a chaque requete cote serveur, et une deuxieme fois
 * dans la base par la Row-Level Security. L'interface, elle, ne fait que
 * masquer des boutons : ce n'est pas une protection.
 */
@Configuration
@EnableMethodSecurity
public class ConfigurationSecurite {

    /**
     * Argon2id aux parametres de Spring Security : 16 octets de sel, 32 de
     * sortie, 1 fil, 16 Mo de memoire, 3 passes.
     */
    @Bean
    public PasswordEncoder encodeurMotDePasse() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /**
     * Le filtre de contexte vit dans la chaine de securite, apres
     * l'authentification. Sans ce desenregistrement, Spring Boot le placerait
     * aussi dans la chaine servlet, ou il s'executerait avant d'avoir un
     * utilisateur a lire, pour rien.
     */
    @Bean
    public FilterRegistrationBean<FiltreContexteOrganisation> pasDEnregistrementAutomatique(
            FiltreContexteOrganisation filtre) {
        var enregistrement = new FilterRegistrationBean<>(filtre);
        enregistrement.setEnabled(false);
        return enregistrement;
    }

    @Bean
    public SecurityFilterChain chaine(HttpSecurity http,
                                      FournisseurAuthentification fournisseur,
                                      SourceDetailsAvecCode sourceDetails,
                                      FiltreContexteOrganisation filtreOrganisation)
            throws Exception {

        var gestionJeton = new CsrfTokenRequestAttributeHandler();

        http
            .authenticationProvider(fournisseur)
            .authorizeHttpRequests(acces -> acces
                // Ce qui doit rester ouvert : la page de connexion, la demande
                // d'inscription, l'acceptation d'une invitation, et la
                // confirmation de paiement de l'agregateur.
                .requestMatchers("/", "/connexion", "/deconnexion",
                                 "/inscription/**", "/invitation/**",
                                 "/css/**", "/js/**", "/webjars/**", "/images/**").permitAll()
                .requestMatchers("/paiements/confirmation").permitAll()
                .requestMatchers("/actuator/health").permitAll()

                // L'administration de la plateforme : les comptes et l'argent,
                // jamais les dossiers des clients.
                .requestMatchers("/administration/**").hasRole("SUPER_ADMIN")

                // La validation est fermee a ceux qui redigent.
                .requestMatchers("/dossiers/*/validation/**")
                    .hasAnyRole("ADMIN_ENTREPRISE", "VALIDATEUR")

                // Modifier la fiche, le coffre, le personnel et le materiel.
                .requestMatchers("/coffre/**", "/personnel/**", "/materiel/**", "/entreprise/**")
                    .hasAnyRole("ADMIN_ENTREPRISE", "REDACTEUR", "OPERATEUR_MANDATE")

                // Charger un DAO et preparer un brouillon.
                .requestMatchers("/appels-offres/**")
                    .hasAnyRole("ADMIN_ENTREPRISE", "REDACTEUR", "OPERATEUR_MANDATE")

                .anyRequest().authenticated())

            .formLogin(connexion -> connexion
                .loginPage("/connexion")
                .loginProcessingUrl("/connexion")
                .usernameParameter("email")
                .passwordParameter("motDePasse")
                .authenticationDetailsSource(sourceDetails)
                .defaultSuccessUrl("/tableau-de-bord", true)
                .failureUrl("/connexion?erreur")
                .permitAll())

            .logout(deconnexion -> deconnexion
                .logoutUrl("/deconnexion")
                .logoutSuccessUrl("/connexion?deconnecte")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))

            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                // Une session volee ne survit pas a la connexion suivante.
                .sessionFixation(fixation -> fixation.newSession())
                .maximumSessions(3))

            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(gestionJeton)
                // L'agregateur de paiement ne connait pas nos jetons : cet appel
                // est authentifie par sa signature, verifiee dans le controleur.
                .ignoringRequestMatchers("/paiements/confirmation"))

            .headers(entetes -> entetes
                .frameOptions(cadre -> cadre.deny())
                .contentTypeOptions(options -> {}))

            .addFilterAfter(filtreOrganisation, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
