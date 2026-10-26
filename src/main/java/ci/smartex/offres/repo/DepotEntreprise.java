package ci.smartex.offres.repo;

import ci.smartex.offres.domain.Entreprise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DepotEntreprise extends JpaRepository<Entreprise, UUID> {

    Optional<Entreprise> findByOrganisationId(UUID organisationId);
}
