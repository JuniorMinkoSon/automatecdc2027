package ci.smartex.offres.repo;

import ci.smartex.offres.domain.Membre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepotMembre extends JpaRepository<Membre, UUID> {

    List<Membre> findAllByUtilisateurId(UUID utilisateurId);

    List<Membre> findAllByOrganisationId(UUID organisationId);

    List<Membre> findAllByOrganisationIdAndRole(UUID organisationId, Membre.Role role);
}
