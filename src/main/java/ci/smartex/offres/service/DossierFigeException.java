package ci.smartex.offres.service;

/**
 * Tentative de modifier un dossier valide ou depose.
 *
 * <p>Une version validee est figee avec son empreinte : la corriger cree une
 * version n+1, elle ne reecrit pas celle qui a ete signee.
 */
public class DossierFigeException extends RuntimeException {

    public DossierFigeException(String message) {
        super(message);
    }
}
