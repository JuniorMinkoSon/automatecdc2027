package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Compte de la plateforme.
 *
 * <p>Le mot de passe est nul tant que l'utilisateur ne l'a pas choisi lui-meme
 * via son lien d'invitation : l'administrateur qui active une entreprise ne
 * connait jamais le mot de passe de son client.
 */
@Entity
@Table(name = "utilisateur")
@Getter
@Setter
public class Utilisateur {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    /** Empreinte Argon2id. Nul tant que l'invitation n'a pas ete honoree. */
    @Column(name = "mot_de_passe_hash")
    private String motDePasseHash;

    @Column(name = "nom_complet", nullable = false)
    private String nomComplet;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(name = "totp_actif", nullable = false)
    private boolean totpActif = false;

    @Column(name = "echecs_connexion", nullable = false)
    private int echecsConnexion = 0;

    @Column(name = "verrouille_jusqu_a")
    private Instant verrouilleJusquA;

    /** Gere les abonnements et les paiements, ne voit aucun dossier. */
    @Column(name = "super_admin", nullable = false)
    private boolean superAdmin = false;

    @Column(name = "jeton_invitation", unique = true)
    private String jetonInvitation;

    @Column(name = "invitation_expire_le")
    private Instant invitationExpireLe;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe = Instant.now();

    public boolean estVerrouille() {
        return verrouilleJusquA != null && verrouilleJusquA.isAfter(Instant.now());
    }

    public boolean aChoisiSonMotDePasse() {
        return motDePasseHash != null && !motDePasseHash.isBlank();
    }
}
