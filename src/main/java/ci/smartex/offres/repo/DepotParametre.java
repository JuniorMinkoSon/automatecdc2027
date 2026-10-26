package ci.smartex.offres.repo;

import ci.smartex.offres.domain.Parametre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DepotParametre extends JpaRepository<Parametre, UUID> {

    Optional<Parametre> findByOrganisationIdAndCle(UUID organisationId, String cle);
}
