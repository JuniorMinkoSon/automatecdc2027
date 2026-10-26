package ci.smartex.offres.securite;

import org.springframework.security.core.AuthenticationException;

/**
 * Mot de passe juste, second facteur faux ou absent.
 *
 * <p>Le message reste le meme que pour un mot de passe faux a l'ecran : dire
 * « mot de passe correct, code invalide » confirmerait le mot de passe a qui
 * le teste.
 */
public class CodeDeuxFacteursInvalideException extends AuthenticationException {

    public CodeDeuxFacteursInvalideException(String message) {
        super(message);
    }
}
