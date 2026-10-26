package ci.smartex.offres.repo;

import ci.smartex.offres.domain.Mandat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DepotMandat extends JpaRepository<Mandat, UUID> {

    Optional<Mandat> findFirstByOrganisationIdAndRetireLeIsNull(UUID organisationId);
}
