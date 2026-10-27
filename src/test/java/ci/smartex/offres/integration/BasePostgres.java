package ci.smartex.offres.integration;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Un vrai PostgreSQL 16, avec le vrai script d'amorcage des roles.
 *
 * <p>Rien de ce qui compte ici ne s'observe ailleurs : la Row-Level Security,
 * les colonnes generees, le declencheur d'immuabilite du journal et la
 * separation des trois comptes n'existent ni en base memoire ni dans un
 * bouchon. Un test qui ne les exerce pas ne prouve rien sur la production.
 *
 * <p>La base est fournie par {@code infra/test/base-de-test.sh}. Les tests se
 * desactivent quand aucune base n'est indiquee, plutot que d'echouer : un
 * poste sans Docker doit pouvoir lancer les tests unitaires sans voir du
 * rouge qui ne lui apprend rien. Mais l'absence de base n'est alors jamais
 * presentee comme un succes - elle apparait comme un test ignore.
 */
@EnabledIfSystemProperty(
        named = "test.postgres.url",
        matches = ".+",
        disabledReason = "Aucune base fournie. Lancez infra/test/base-de-test.sh demarrer.")
public abstract class BasePostgres {

    protected static String urlJdbc() {
        return System.getProperty("test.postgres.url");
    }

    protected static String mdpApp() {
        return System.getProperty("test.postgres.mdp.app", "app-de-test");
    }

    protected static String mdpMigration() {
        return System.getProperty("test.postgres.mdp.migration", "migration-de-test");
    }

    protected static String mdpLecture() {
        return System.getProperty("test.postgres.mdp.lecture", "lecture-de-test");
    }

    /**
     * L'application se connecte comme app, Flyway comme migration : c'est la
     * configuration de production, et c'est la seule qui prouve que le compte
     * applicatif n'a pas besoin de droits sur la structure.
     */
    @DynamicPropertySource
    static void configurer(DynamicPropertyRegistry proprietes) {
        proprietes.add("spring.datasource.url", BasePostgres::urlJdbc);
        proprietes.add("spring.datasource.username", () -> "app");
        proprietes.add("spring.datasource.password", BasePostgres::mdpApp);

        proprietes.add("spring.flyway.url", BasePostgres::urlJdbc);
        proprietes.add("spring.flyway.user", () -> "migration");
        proprietes.add("spring.flyway.password", BasePostgres::mdpMigration);
        // La base de test repart vierge a chaque demarrage du conteneur, mais
        // pas entre deux executions de Maven : nettoyer ici garantit que la
        // migration est bien rejouee sur du neuf a chaque fois.
        proprietes.add("spring.flyway.clean-disabled", () -> "false");

        // Hibernate compare les entites au schema produit par Flyway. Tout
        // ecart fait echouer le demarrage, ce qui est exactement le but.
        proprietes.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        // Les taches de fond ne sont pas le sujet de ces tests, et un
        // ordonnanceur qui demarre ajoute du bruit et des courses.
        proprietes.add("org.jobrunr.background-job-server.enabled", () -> "false");
        proprietes.add("org.jobrunr.dashboard.enabled", () -> "false");

        proprietes.add("app.ia.active", () -> "false");
        proprietes.add("app.cle-chiffrement-fichiers", () -> "cle-de-test-non-sensible");
        proprietes.add("app.cle-liens-signes", () -> "cle-de-test-non-sensible");
    }
}
