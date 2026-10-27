package ci.smartex.offres.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ce que seule une vraie base peut prouver.
 *
 * <p>Le demarrage du contexte Spring est deja un test : Flyway migre une base
 * vierge, puis Hibernate compare les vingt-cinq entites au schema obtenu. Un
 * seul ecart de nom, de type ou de nullabilite fait echouer la classe entiere.
 *
 * <p>Le reste s'ecrit en JDBC brut, en se connectant comme le compte
 * applicatif. Passer par les depots Spring testerait le filtrage de
 * l'application ; ici on veut l'inverse : verifier que la base refuse d'
 * elle-meme, meme quand la requete ne filtre rien.
 */
@SpringBootTest
@DisplayName("Schema PostgreSQL et isolation entre entreprises")
class SchemaEtIsolationTest extends BasePostgres {

    private static final UUID ORGANISATION_A = UUID.randomUUID();
    private static final UUID ORGANISATION_B = UUID.randomUUID();

    @Test
    @DisplayName("le contexte demarre : Flyway a migre et Hibernate a valide les entites")
    void contexteDemarre() {
        // Vide a dessein. Si cette methode s'execute, c'est que la migration a
        // abouti sur une base vierge et que ddl-auto=validate n'a trouve
        // aucun ecart entre les entites et le schema.
        assertThat(urlJdbc()).contains("postgresql");
    }

    @Nested
    @DisplayName("Row-Level Security")
    class Isolation {

        @Test
        @DisplayName("une organisation ne voit jamais les dossiers d'une autre")
        void cloisonnementDesDossiers() throws SQLException {
            UUID dossierA;
            UUID dossierB;

            try (Connection connexion = connexionApp()) {
                preparerOrganisation(connexion, ORGANISATION_A, "Entreprise A");
                preparerOrganisation(connexion, ORGANISATION_B, "Entreprise B");
                dossierA = creerDossier(connexion, ORGANISATION_A);
                dossierB = creerDossier(connexion, ORGANISATION_B);
            }

            try (Connection connexion = connexionApp()) {
                poserOrganisation(connexion, ORGANISATION_A);

                // Requete volontairement sans filtre : c'est la base qui doit
                // trancher, pas la clause WHERE de l'application.
                assertThat(identifiants(connexion, "SELECT id FROM dossier"))
                        .contains(dossierA)
                        .doesNotContain(dossierB);
            }
        }

        @Test
        @DisplayName("une organisation ne peut pas ecrire chez une autre")
        void ecritureCroiseeRefusee() throws SQLException {
            try (Connection connexion = connexionApp()) {
                preparerOrganisation(connexion, ORGANISATION_A, "Entreprise A");
                preparerOrganisation(connexion, ORGANISATION_B, "Entreprise B");

                poserOrganisation(connexion, ORGANISATION_A);
                UUID appelOffres = creerAppelOffres(connexion, ORGANISATION_A);

                // Toujours dans le contexte de A, mais en marquant la ligne au
                // nom de B. C'est la tentative qu'un bogue applicatif
                // produirait : la clause WITH CHECK doit la refuser.
                assertThatThrownBy(() ->
                        insererDossier(connexion, ORGANISATION_B, appelOffres))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("row-level security");
            }
        }

        @Test
        @DisplayName("sans organisation posee, la base ne renvoie rien")
        void absenceDeContexteNeDonneRien() throws SQLException {
            try (Connection connexion = connexionApp()) {
                preparerOrganisation(connexion, ORGANISATION_A, "Entreprise A");
                creerDossier(connexion, ORGANISATION_A);
            }

            try (Connection connexion = connexionApp()) {
                // Aucun set_config : le reglage est absent, app_organisation()
                // rend NULL, et aucune politique ne laisse passer de ligne.
                // L'oubli echoue donc du bon cote.
                assertThat(identifiants(connexion, "SELECT id FROM dossier")).isEmpty();
            }
        }

        @Test
        @DisplayName("une connexion rendue au pool ne garde pas l'organisation precedente")
        void pasDeFuiteEntreDeuxUsages() throws SQLException {
            try (Connection connexion = connexionApp()) {
                preparerOrganisation(connexion, ORGANISATION_A, "Entreprise A");
                creerDossier(connexion, ORGANISATION_A);

                poserOrganisation(connexion, ORGANISATION_A);
                assertThat(identifiants(connexion, "SELECT id FROM dossier")).isNotEmpty();

                // C'est ce que fait FournisseurConnexionLocataire au retour
                // d'une connexion au pool.
                try (Statement ordre = connexion.createStatement()) {
                    ordre.execute("RESET app.organisation_id");
                    ordre.execute("RESET app.super_admin");
                }

                assertThat(identifiants(connexion, "SELECT id FROM dossier")).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Garanties de la base")
    class GarantiesBase {

        @Test
        @DisplayName("le montant d'une ligne de DQE est calcule, pas saisi")
        void montantCalculeParLaBase() throws SQLException {
            try (Connection connexion = connexionApp()) {
                preparerOrganisation(connexion, ORGANISATION_A, "Entreprise A");
                UUID dossier = creerDossier(connexion, ORGANISATION_A);
                poserOrganisation(connexion, ORGANISATION_A);

                try (var requete = connexion.prepareStatement("""
                        INSERT INTO ligne_dqe (organisation_id, dossier_id, section,
                                               numero_prix, designation, unite,
                                               quantite, prix_unitaire)
                        VALUES (?, ?, 'I', '0', 'Debroussement', 'm2', 828.000, 500)
                        RETURNING montant
                        """)) {
                    requete.setObject(1, ORGANISATION_A);
                    requete.setObject(2, dossier);
                    try (ResultSet resultat = requete.executeQuery()) {
                        assertThat(resultat.next()).isTrue();
                        assertThat(resultat.getLong("montant")).isEqualTo(414_000L);
                    }
                }

                // Ecrire le montant a la main est refuse par PostgreSQL : c'est
                // ce qui rend l'erreur arithmetique impossible plutot que
                // rattrapee.
                assertThatThrownBy(() -> {
                    try (Statement ordre = connexion.createStatement()) {
                        ordre.execute("UPDATE ligne_dqe SET montant = 1");
                    }
                }).isInstanceOf(SQLException.class)
                  .hasMessageContaining("generated column");
            }
        }

        @Test
        @DisplayName("un prix modifie entraine le recalcul du montant")
        void recalculSurMiseAJour() throws SQLException {
            try (Connection connexion = connexionApp()) {
                preparerOrganisation(connexion, ORGANISATION_A, "Entreprise A");
                UUID dossier = creerDossier(connexion, ORGANISATION_A);
                poserOrganisation(connexion, ORGANISATION_A);

                try (var requete = connexion.prepareStatement("""
                        INSERT INTO ligne_dqe (organisation_id, dossier_id, section,
                                               numero_prix, quantite, prix_unitaire)
                        VALUES (?, ?, 'I', '1', 2.000, 100)
                        """)) {
                    requete.setObject(1, ORGANISATION_A);
                    requete.setObject(2, dossier);
                    requete.executeUpdate();
                }

                try (Statement ordre = connexion.createStatement()) {
                    ordre.execute("UPDATE ligne_dqe SET prix_unitaire = 250");
                    try (ResultSet resultat = ordre.executeQuery(
                            "SELECT montant FROM ligne_dqe")) {
                        assertThat(resultat.next()).isTrue();
                        assertThat(resultat.getLong(1)).isEqualTo(500L);
                    }
                }
            }
        }

        @Test
        @DisplayName("le journal est en ajout seul, meme pour le compte applicatif")
        void journalImmuable() throws SQLException {
            try (Connection connexion = connexionApp()) {
                preparerOrganisation(connexion, ORGANISATION_A, "Entreprise A");
                poserOrganisation(connexion, ORGANISATION_A);

                try (var requete = connexion.prepareStatement("""
                        INSERT INTO journal (organisation_id, action, objet_type)
                        VALUES (?, 'TEST', 'dossier')
                        """)) {
                    requete.setObject(1, ORGANISATION_A);
                    requete.executeUpdate();
                }

                assertThatThrownBy(() -> {
                    try (Statement ordre = connexion.createStatement()) {
                        ordre.execute("UPDATE journal SET action = 'EFFACE'");
                    }
                }).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> {
                    try (Statement ordre = connexion.createStatement()) {
                        ordre.execute("DELETE FROM journal");
                    }
                }).isInstanceOf(SQLException.class);
            }
        }

        @Test
        @DisplayName("le compte applicatif ne peut pas toucher a la structure")
        void appNaAucunDroitSurLaStructure() throws SQLException {
            try (Connection connexion = connexionApp()) {
                assertThatThrownBy(() -> {
                    try (Statement ordre = connexion.createStatement()) {
                        ordre.execute("CREATE TABLE tentative (id int)");
                    }
                }).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> {
                    try (Statement ordre = connexion.createStatement()) {
                        ordre.execute("DROP TABLE dossier");
                    }
                }).isInstanceOf(SQLException.class);
            }
        }

        @Test
        @DisplayName("le compte de lecture ne peut pas ecrire")
        void lectureEstVraimentEnLectureSeule() throws SQLException {
            try (Connection connexion = DriverManager.getConnection(
                    urlJdbc(), "lecture", mdpLecture())) {

                assertThatThrownBy(() -> {
                    try (Statement ordre = connexion.createStatement()) {
                        ordre.execute("INSERT INTO organisation (nom) VALUES ('X')");
                    }
                }).isInstanceOf(SQLException.class);
            }
        }
    }

    // ------------------------------------------------------------------ outils

    private static Connection connexionApp() throws SQLException {
        return DriverManager.getConnection(urlJdbc(), "app", mdpApp());
    }

    /**
     * Pose l'organisation sur la connexion, comme le fait
     * FournisseurConnexionLocataire avant de la preter a Hibernate.
     */
    private static void poserOrganisation(Connection connexion, UUID organisation)
            throws SQLException {
        try (var requete = connexion.prepareStatement(
                "SELECT set_config('app.organisation_id', ?, false)")) {
            requete.setString(1, organisation.toString());
            requete.execute();
        }
    }

    private static void preparerOrganisation(Connection connexion, UUID id, String nom)
            throws SQLException {
        // L'organisation se cree en contexte super-administrateur : c'est lui
        // qui active une entreprise apres paiement.
        try (Statement ordre = connexion.createStatement()) {
            ordre.execute("SELECT set_config('app.super_admin', 'on', false)");
        }
        try (var requete = connexion.prepareStatement(
                "INSERT INTO organisation (id, nom, statut) VALUES (?, ?, 'ACTIVE') "
                        + "ON CONFLICT (id) DO NOTHING")) {
            requete.setObject(1, id);
            requete.setString(2, nom);
            requete.executeUpdate();
        }
        try (Statement ordre = connexion.createStatement()) {
            ordre.execute("SELECT set_config('app.super_admin', 'off', false)");
        }
    }

    private static UUID creerDossier(Connection connexion, UUID organisation)
            throws SQLException {
        poserOrganisation(connexion, organisation);
        return insererDossier(connexion, organisation, creerAppelOffres(connexion, organisation));
    }

    private static UUID creerAppelOffres(Connection connexion, UUID organisation)
            throws SQLException {
        UUID appelOffres = UUID.randomUUID();
        try (var requete = connexion.prepareStatement("""
                INSERT INTO appel_offres (id, organisation_id, intitule, statut)
                VALUES (?, ?, 'Marche de test', 'A_VERIFIER')
                """)) {
            requete.setObject(1, appelOffres);
            requete.setObject(2, organisation);
            requete.executeUpdate();
        }
        return appelOffres;
    }

    /**
     * Insere sans toucher au contexte : c'est ce qui permet d'ecrire au nom
     * d'une organisation alors qu'une autre est posee, et donc de verifier que
     * la base refuse.
     */
    private static UUID insererDossier(Connection connexion,
                                       UUID organisation,
                                       UUID appelOffres) throws SQLException {
        UUID dossier = UUID.randomUUID();
        try (var requete = connexion.prepareStatement("""
                INSERT INTO dossier (id, organisation_id, appel_offres_id, statut, version)
                VALUES (?, ?, ?, 'BROUILLON', 1)
                """)) {
            requete.setObject(1, dossier);
            requete.setObject(2, organisation);
            requete.setObject(3, appelOffres);
            requete.executeUpdate();
        }
        return dossier;
    }

    private static java.util.List<UUID> identifiants(Connection connexion, String sql)
            throws SQLException {
        java.util.List<UUID> trouves = new java.util.ArrayList<>();
        try (Statement ordre = connexion.createStatement();
             ResultSet resultat = ordre.executeQuery(sql)) {
            while (resultat.next()) {
                trouves.add(resultat.getObject(1, UUID.class));
            }
        }
        return trouves;
    }
}
