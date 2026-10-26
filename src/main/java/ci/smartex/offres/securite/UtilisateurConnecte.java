package ci.smartex.offres.securite;

import ci.smartex.offres.domain.Membre;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * L'utilisateur authentifie, avec ses appartenances.
 *
 * <p>Un utilisateur peut etre membre de plusieurs organisations, et c'est le
 * cas normal de l'operateur mandate. L'organisation active est portee ici,
 * dans la session, et nulle part ailleurs : aucune page ne l'accepte en
 * parametre.
 */
public class UtilisateurConnecte implements UserDetails {

    private final UUID utilisateurId;
    private final String email;
    private final String nomComplet;
    private final String motDePasseHash;
    private final boolean actif;
    private final boolean superAdmin;
    private final boolean totpActif;
    private final Map<UUID, Membre.Role> appartenances;
    private UUID organisationActive;

    public UtilisateurConnecte(UUID utilisateurId,
                               String email,
                               String nomComplet,
                               String motDePasseHash,
                               boolean actif,
                               boolean superAdmin,
                               boolean totpActif,
                               Map<UUID, Membre.Role> appartenances) {
        this.utilisateurId = utilisateurId;
        this.email = email;
        this.nomComplet = nomComplet;
        this.motDePasseHash = motDePasseHash;
        this.actif = actif;
        this.superAdmin = superAdmin;
        this.totpActif = totpActif;
        this.appartenances = Map.copyOf(appartenances);
        this.organisationActive = appartenances.keySet().stream().findFirst().orElse(null);
    }

    /**
     * Les droits ne valent que dans l'organisation active : changer
     * d'organisation change les autorites, il ne les cumule pas.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (superAdmin) {
            return List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        }
        if (organisationActive == null) {
            return List.of();
        }
        return appartenances.entrySet().stream()
                .filter(entree -> entree.getKey().equals(organisationActive))
                .map(entree -> new SimpleGrantedAuthority(entree.getValue().autorite()))
                .collect(Collectors.toList());
    }

    public Optional<Membre.Role> roleActif() {
        return organisationActive == null
                ? Optional.empty()
                : Optional.ofNullable(appartenances.get(organisationActive));
    }

    /**
     * Bascule vers une autre organisation, et seulement vers une organisation
     * dont l'utilisateur est deja membre.
     */
    public void basculerVers(UUID organisationId) {
        if (!appartenances.containsKey(organisationId)) {
            throw new IllegalArgumentException(
                    "L'utilisateur n'est pas membre de cette organisation.");
        }
        this.organisationActive = organisationId;
    }

    public UUID getUtilisateurId() {
        return utilisateurId;
    }

    public String getNomComplet() {
        return nomComplet;
    }

    public boolean isSuperAdmin() {
        return superAdmin;
    }

    public boolean isTotpActif() {
        return totpActif;
    }

    public UUID getOrganisationActive() {
        return organisationActive;
    }

    public Map<UUID, Membre.Role> getAppartenances() {
        return appartenances;
    }

    @Override
    public String getPassword() {
        return motDePasseHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return actif;
    }
}
