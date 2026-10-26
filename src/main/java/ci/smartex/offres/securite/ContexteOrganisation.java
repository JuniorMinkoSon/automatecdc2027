package ci.smartex.offres.securite;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * L'organisation dans laquelle le fil d'execution courant travaille.
 *
 * <p>Elle est posee a partir de la session de l'utilisateur, ou par une tache
 * de fond avant de traiter une organisation donnee. Elle n'est jamais lue dans
 * un parametre envoye par le navigateur : c'est precisement la ou un attaquant
 * irait la chercher.
 *
 * <p>La valeur posee ici est recopiee dans la connexion PostgreSQL par
 * {@code FournisseurConnexionLocataire}, et c'est la base qui s'en sert pour
 * filtrer. Oublier de la poser ne donne donc pas acces a tout : cela donne
 * acces a rien.
 */
public final class ContexteOrganisation {

    /** Ce que le resolveur Hibernate renvoie quand aucune organisation n'est posee. */
    public static final String AUCUNE = "aucune";

    /** Le super-administrateur : voit les comptes et les paiements, aucun dossier. */
    public static final String SUPER_ADMIN = "super-admin";

    private static final ThreadLocal<String> COURANTE = ThreadLocal.withInitial(() -> AUCUNE);

    private ContexteOrganisation() {
    }

    public static Optional<UUID> organisationCourante() {
        String valeur = COURANTE.get();
        if (AUCUNE.equals(valeur) || SUPER_ADMIN.equals(valeur)) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(valeur));
    }

    /**
     * L'organisation courante, ou une erreur. A utiliser partout ou travailler
     * sans organisation serait un bogue plutot qu'un cas normal.
     */
    public static UUID exigerOrganisation() {
        return organisationCourante().orElseThrow(() -> new IllegalStateException(
                "Aucune organisation dans le contexte : la requete ne peut pas aboutir."));
    }

    public static boolean estSuperAdmin() {
        return SUPER_ADMIN.equals(COURANTE.get());
    }

    public static String identifiantLocataire() {
        return COURANTE.get();
    }

    public static void poser(UUID organisationId) {
        COURANTE.set(organisationId.toString());
    }

    public static void poserSuperAdmin() {
        COURANTE.set(SUPER_ADMIN);
    }

    public static void effacer() {
        COURANTE.remove();
    }

    /**
     * Execute un traitement dans le contexte d'une organisation, puis rend le
     * fil d'execution tel qu'il etait. Les taches de fond passent par ici : une
     * tache qui traite dix organisations ne doit pas laisser la onzieme heriter
     * du contexte de la dixieme.
     */
    public static <T> T dans(UUID organisationId, Callable<T> traitement) {
        String precedent = COURANTE.get();
        COURANTE.set(organisationId.toString());
        try {
            return traitement.call();
        } catch (RuntimeException erreur) {
            throw erreur;
        } catch (Exception erreur) {
            throw new IllegalStateException("Echec du traitement dans l'organisation "
                    + organisationId, erreur);
        } finally {
            COURANTE.set(precedent);
        }
    }

    public static void dans(UUID organisationId, Runnable traitement) {
        dans(organisationId, () -> {
            traitement.run();
            return null;
        });
    }
}
