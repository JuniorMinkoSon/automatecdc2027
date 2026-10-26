package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Lien utilisateur / organisation / role.
 *
 * <p>C'est la seule porte d'entree : un utilisateur n'agit que dans les
 * organisations dont il est membre, et l'organisation active est lue dans sa
 * session, jamais dans un parametre envoye par le navigateur.
 */
@Entity
@Table(name = "membre")
@Getter
@Setter
public class Membre {

    /** Les roles de la matrice de la section 10 du cahier des charges. */
    public enum Role {
        /** Gere les utilisateurs de son entreprise, valide et verrouille. */
        ADMIN_ENTREPRISE,
        /** Prepare les brouillons ; ne peut pas valider ce qu'il a redige. */
        REDACTEUR,
        /** Valide et verrouille la version finale. */
        VALIDATEUR,
        /** Telecharge les PDF valides, rien de plus. */
        LECTEUR,
        /** L'equipe de la plateforme, en service cle en main, sous mandat. */
        OPERATEUR_MANDATE;

        public String autorite() {
            return "ROLE_" + name();
        }
    }

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "organisation_id", nullable = false, columnDefinition = "uuid")
    private UUID organisationId;

    @Column(name = "utilisateur_id", nullable = false, columnDefinition = "uuid")
    private UUID utilisateurId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe = Instant.now();
}
