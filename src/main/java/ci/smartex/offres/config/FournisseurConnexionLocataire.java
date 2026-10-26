package ci.smartex.offres.config;

import ci.smartex.offres.securite.ContexteOrganisation;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Recopie l'organisation courante dans la connexion PostgreSQL.
 *
 * <p>C'est le seul endroit qui fait le pont entre le contexte applicatif et la
 * Row-Level Security. Chaque connexion empruntee au pool est marquee avant
 * usage et demarquee avant d'etre rendue : une connexion ne peut pas arriver
 * chez l'utilisateur suivant en portant encore l'organisation du precedent.
 *
 * <p>Quand aucune organisation n'est posee, le reglage est vide plutot
 * qu'absent : {@code app_organisation()} renvoie alors NULL, et les politiques
 * ne laissent passer aucune ligne. L'oubli echoue donc du bon cote.
 */
@Component
public class FournisseurConnexionLocataire implements MultiTenantConnectionProvider<String> {

    private final DataSource sourceDeDonnees;

    public FournisseurConnexionLocataire(DataSource sourceDeDonnees) {
        this.sourceDeDonnees = sourceDeDonnees;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return sourceDeDonnees.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connexion) throws SQLException {
        connexion.close();
    }

    @Override
    public Connection getConnection(String locataire) throws SQLException {
        Connection connexion = sourceDeDonnees.getConnection();
        appliquer(connexion, locataire);
        return connexion;
    }

    @Override
    public void releaseConnection(String locataire, Connection connexion) throws SQLException {
        try (Statement ordre = connexion.createStatement()) {
            ordre.execute("RESET app.organisation_id");
            ordre.execute("RESET app.super_admin");
        } finally {
            connexion.close();
        }
    }

    private void appliquer(Connection connexion, String locataire) throws SQLException {
        boolean superAdmin = ContexteOrganisation.SUPER_ADMIN.equals(locataire);
        boolean organisation = !superAdmin && !ContexteOrganisation.AUCUNE.equals(locataire);

        // set_config plutot qu'un SET interpole : l'identifiant ne touche jamais
        // le texte de la requete, donc rien a injecter meme si la valeur venait
        // d'ailleurs que de la session.
        try (PreparedStatement ordre =
                     connexion.prepareStatement("SELECT set_config('app.organisation_id', ?, false),"
                             + " set_config('app.super_admin', ?, false)")) {
            ordre.setString(1, organisation ? locataire : "");
            ordre.setString(2, superAdmin ? "on" : "off");
            ordre.execute();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        // Le marquage vit sur la connexion : la rendre au milieu d'une
        // transaction la ferait revenir sans organisation.
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> type) {
        return MultiTenantConnectionProvider.class.equals(type)
                || FournisseurConnexionLocataire.class.equals(type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> type) {
        if (isUnwrappableAs(type)) {
            return (T) this;
        }
        throw new IllegalArgumentException("Type non supporte : " + type);
    }
}
