package ci.smartex.offres.rendu;

/** Le rendu documentaire a echoue : ni PDF, ni DOCX, ni assemblage. */
public class RenduImpossibleException extends RuntimeException {

    public RenduImpossibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
