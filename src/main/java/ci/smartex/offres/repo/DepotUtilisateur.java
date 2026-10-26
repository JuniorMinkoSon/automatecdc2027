package ci.smartex.offres.repo;

import ci.smartex.offres.domain.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DepotUtilisateur extends JpaRepository<Utilisateur, UUID> {

    Optional<Utilisateur> findByEmailIgnoreCase(String email);

    Optional<Utilisateur> findByJetonInvitation(String jeton);
}
