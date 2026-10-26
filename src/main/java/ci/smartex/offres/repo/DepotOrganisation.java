package ci.smartex.offres.repo;

import ci.smartex.offres.domain.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepotOrganisation extends JpaRepository<Organisation, UUID> {

    List<Organisation> findAllByStatut(Organisation.Statut statut);
}
