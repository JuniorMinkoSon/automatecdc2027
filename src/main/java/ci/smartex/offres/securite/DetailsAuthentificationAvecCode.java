package ci.smartex.offres.securite;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/** Porte le code a usage unique saisi sur le formulaire de connexion. */
public class DetailsAuthentificationAvecCode extends WebAuthenticationDetails {

    private final String codeUnique;

    public DetailsAuthentificationAvecCode(HttpServletRequest requete) {
        super(requete);
        this.codeUnique = requete.getParameter("code");
    }

    public String getCodeUnique() {
        return codeUnique;
    }
}
