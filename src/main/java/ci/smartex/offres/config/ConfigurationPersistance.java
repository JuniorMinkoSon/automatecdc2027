package ci.smartex.offres.config;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Branche la Row-Level Security sur Hibernate.
 *
 * <p>Sans ces deux reglages, le fournisseur de connexions et le resolveur
 * existeraient dans le contexte Spring sans jamais etre appeles, et chaque
 * requete partirait vers PostgreSQL sans organisation. Les politiques
 * refuseraient alors toutes les lignes : la panne serait visible tout de suite,
 * et non silencieuse.
 */
@Configuration
public class ConfigurationPersistance {

    @Bean
    public HibernatePropertiesCustomizer isolationParOrganisation(
            FournisseurConnexionLocataire fournisseur,
            ResolveurLocataire resolveur) {

        return proprietes -> {
            proprietes.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, fournisseur);
            proprietes.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolveur);
        };
    }
}
