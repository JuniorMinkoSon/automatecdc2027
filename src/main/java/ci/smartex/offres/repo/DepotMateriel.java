package ci.smartex.offres.repo;

import ci.smartex.offres.domain.Materiel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepotMateriel extends JpaRepository<Materiel, UUID> {

    List<Materiel> findAllByOrganisationId(UUID organisationId);
}
