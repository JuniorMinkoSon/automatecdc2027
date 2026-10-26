package ci.smartex.offres.securite;

import org.springframework.security.authentication.LockedException;

/** Leve apres cinq echecs : le compte se rouvre seul, sans intervention. */
public class CompteVerrouilleException extends LockedException {

    public CompteVerrouilleException(String message) {
        super(message);
    }
}
