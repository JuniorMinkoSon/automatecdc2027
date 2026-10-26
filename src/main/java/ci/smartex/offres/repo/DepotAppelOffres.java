package ci.smartex.offres.repo;

import ci.smartex.offres.domain.AppelOffres;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepotAppelOffres extends JpaRepository<AppelOffres, UUID> {

    List<AppelOffres> findAllByOrganisationIdOrderByCreeLeDesc(UUID organisationId);
}
