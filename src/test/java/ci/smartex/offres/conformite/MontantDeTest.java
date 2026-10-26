package ci.smartex.offres.conformite;

import ci.smartex.offres.domain.LigneDqe;

import java.lang.reflect.Field;

/**
 * Pose le montant d'une ligne de DQE dans un test.
 *
 * <p>En production ce champ n'a pas de setter : PostgreSQL le calcule et
 * Hibernate le relit. Hors base, il faut bien le renseigner pour exercer les
 * controles. Passer par la reflexion plutot que par un setter garde la regle
 * intacte la ou elle compte, dans le code qui tourne.
 */
final class MontantDeTest {

    private MontantDeTest() {
    }

    static void poser(LigneDqe ligne, long montant) {
        try {
            Field champ = LigneDqe.class.getDeclaredField("montant");
            champ.setAccessible(true);
            champ.set(ligne, montant);
        } catch (ReflectiveOperationException erreur) {
            throw new IllegalStateException(
                    "Le champ montant de LigneDqe a change de nom ; adaptez ce fixture.", erreur);
        }
    }
}
