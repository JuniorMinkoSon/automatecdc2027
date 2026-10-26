package ci.smartex.offres.securite;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Pose l'organisation de la session sur le fil d'execution de la requete, et
 * l'enleve a la fin.
 *
 * <p>Le {@code finally} n'est pas une precaution de style : les fils
 * d'execution d'un serveur sont recycles, et une organisation oubliee sur un
 * fil serait heritee par la requete suivante, celle d'un autre client.
 */
@Component
public class FiltreContexteOrganisation extends OncePerRequestFilter {


    @Override
    protected void doFilterInternal(HttpServletRequest requete,
                                    HttpServletResponse reponse,
                                    FilterChain chaine) throws ServletException, IOException {
        try {
            poserDepuisLaSession();
            chaine.doFilter(requete, reponse);
        } finally {
            ContexteOrganisation.effacer();
        }
    }

    private void poserDepuisLaSession() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || !authentification.isAuthenticated()) {
            return;
        }
        if (!(authentification.getPrincipal() instanceof UtilisateurConnecte utilisateur)) {
            return;
        }

        if (utilisateur.isSuperAdmin()) {
            ContexteOrganisation.poserSuperAdmin();
        } else if (utilisateur.getOrganisationActive() != null) {
            ContexteOrganisation.poser(utilisateur.getOrganisationActive());
        }
    }
}
