package ci.smartex.offres.extraction;

/**
 * L'extraction n'a pas abouti.
 *
 * <p>Ce n'est pas une impasse : le DAO reste televerse, l'appel d'offres passe
 * en echec d'analyse et l'utilisateur saisit les champs a la main. Une panne
 * de l'IA ralentit le travail, elle ne l'arrete pas.
 */
public class ExtractionImpossibleException extends RuntimeException {

    public ExtractionImpossibleException(String message) {
        super(message);
    }

    public ExtractionImpossibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
