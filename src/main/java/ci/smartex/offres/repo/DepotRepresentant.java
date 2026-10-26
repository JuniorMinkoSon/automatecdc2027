package ci.smartex.offres.repo;

import ci.smartex.offres.domain.Representant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepotRepresentant extends JpaRepository<Representant, UUID> {

    List<Representant> findAllByOrganisationId(UUID organisationId);

    Optional<Representant> findFirstByOrganisationIdAndSignataireDefautTrue(UUID organisationId);
}
