package ci.smartex.offres.securite;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.stereotype.Component;

@Component
public class SourceDetailsAvecCode
        implements AuthenticationDetailsSource<HttpServletRequest, DetailsAuthentificationAvecCode> {

    @Override
    public DetailsAuthentificationAvecCode buildDetails(HttpServletRequest requete) {
        return new DetailsAuthentificationAvecCode(requete);
    }
}
