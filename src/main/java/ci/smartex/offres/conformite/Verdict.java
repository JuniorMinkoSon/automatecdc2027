package ci.smartex.offres.conformite;

/**
 * Le resultat d'un controle sur un dossier.
 *
 * @param reussi          vrai si la regle est satisfaite
 * @param message         ce qui ne va pas, en francais, dit a qui doit corriger
 * @param lienCorrection  ou aller corriger, pour que le clic sur une ligne
 *                        rouge mene directement au bon ecran
 */
public record Verdict(boolean reussi, String message, String lienCorrection) {

    private static final Verdict CONFORME = new Verdict(true, null, null);

    public static Verdict conforme() {
        return CONFORME;
    }

    public static Verdict echec(String message) {
        return new Verdict(false, message, null);
    }

    public static Verdict echec(String message, String lienCorrection) {
        return new Verdict(false, message, lienCorrection);
    }

    /** Pour les regles qui s'ecrivent naturellement comme une condition. */
    public static Verdict exiger(boolean condition, String messageSiFaux, String lienCorrection) {
        return condition ? conforme() : echec(messageSiFaux, lienCorrection);
    }
}
