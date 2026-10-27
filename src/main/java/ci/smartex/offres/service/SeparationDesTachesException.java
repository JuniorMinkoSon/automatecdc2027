package ci.smartex.offres.service;

/** Celui qui a modifie un dossier ne peut pas le valider. */
public class SeparationDesTachesException extends RuntimeException {

    public SeparationDesTachesException(String message) {
        super(message);
    }
}
