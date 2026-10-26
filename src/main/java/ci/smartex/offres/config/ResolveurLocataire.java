package ci.smartex.offres.config;

import ci.smartex.offres.securite.ContexteOrganisation;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Dit a Hibernate dans quelle organisation la requete courante travaille.
 *
 * <p>Ne renvoie jamais {@code null} : en l'absence de contexte, il renvoie
 * {@code aucune}, et la base ne laisse alors passer aucune ligne metier.
 */
@Component
public class ResolveurLocataire implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return ContexteOrganisation.identifiantLocataire();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // Une session ouverte pour une organisation ne doit pas servir a une autre.
        return true;
    }
}
