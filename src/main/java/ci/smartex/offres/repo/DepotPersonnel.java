package ci.smartex.offres.repo;

import ci.smartex.offres.domain.Personnel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepotPersonnel extends JpaRepository<Personnel, UUID> {

    List<Personnel> findAllByOrganisationId(UUID organisationId);
}
