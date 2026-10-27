package ci.smartex.offres.web;

import ci.smartex.offres.securite.UtilisateurConnecte;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * La page de connexion et la porte d'entree.
 *
 * <p>Un visiteur deja connecte qui revient sur la racine est renvoye a son
 * tableau de bord plutot qu'a un formulaire qu'il n'a pas a remplir.
 */
@Controller
public class ControleurConnexion {

    @GetMapping("/connexion")
    public String connexion(Authentication authentification) {
        return estConnecte(authentification) ? "redirect:/tableau-de-bord" : "connexion";
    }

    @GetMapping("/")
    public String racine(Authentication authentification) {
        return estConnecte(authentification) ? "redirect:/tableau-de-bord" : "redirect:/connexion";
    }

    private boolean estConnecte(Authentication authentification) {
        return authentification != null
                && authentification.isAuthenticated()
                && authentification.getPrincipal() instanceof UtilisateurConnecte;
    }
}
