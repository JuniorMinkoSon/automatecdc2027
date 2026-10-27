package ci.smartex.offres.service;

import ci.smartex.offres.conformite.RapportConformite;

/**
 * Un controle bloquant echoue : le dossier ne peut ni partir en validation ni
 * etre valide.
 *
 * <p>Porte le rapport avec elle, pour que l'ecran puisse afficher ce qui
 * bloque plutot qu'un refus sans explication.
 */
public class ControleBloquantException extends RuntimeException {

    private final transient RapportConformite rapport;

    public ControleBloquantException(String message, RapportConformite rapport) {
        super(message);
        this.rapport = rapport;
    }

    public RapportConformite getRapport() {
        return rapport;
    }
}
