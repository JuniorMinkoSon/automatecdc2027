package ci.smartex.offres.repo;

import ci.smartex.offres.domain.ReferenceMarche;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepotReferenceMarche extends JpaRepository<ReferenceMarche, UUID> {

    List<ReferenceMarche> findAllByOrganisationId(UUID organisationId);
}
