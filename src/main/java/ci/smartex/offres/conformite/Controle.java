package ci.smartex.offres.conformite;

import ci.smartex.offres.domain.ControleResultat;

/**
 * Une regle que le dossier doit satisfaire.
 *
 * <p>Un controle bloquant qui echoue interdit la validation ; un avertissement
 * s'affiche et laisse la decision au validateur. La frontiere entre les deux
 * n'est pas une question de gravite mais de certitude : on ne bloque que ce
 * que la plateforme sait juger sans se tromper.
 */
public interface Controle {

    String code();

    String libelle();

    ControleResultat.Niveau niveau();

    Verdict verifier(DossierAControler dossier);

    /** Certains controles ne s'appliquent qu'a un dossier qui a un lot, par exemple. */
    default boolean sApplique(DossierAControler dossier) {
        return true;
    }
}
