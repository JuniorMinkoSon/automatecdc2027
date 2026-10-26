package ci.smartex.offres.repo;

import ci.smartex.offres.domain.Abonnement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepotAbonnement extends JpaRepository<Abonnement, UUID> {

    Optional<Abonnement> findFirstByOrganisationIdOrderByFinDesc(UUID organisationId);

    List<Abonnement> findAllByStatut(Abonnement.Statut statut);
}
