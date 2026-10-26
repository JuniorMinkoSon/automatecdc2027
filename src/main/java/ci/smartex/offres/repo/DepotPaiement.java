package ci.smartex.offres.repo;

import ci.smartex.offres.domain.Paiement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepotPaiement extends JpaRepository<Paiement, UUID> {

    /** La reference de l'agregateur rend la confirmation idempotente. */
    Optional<Paiement> findByReference(String reference);

    List<Paiement> findAllByOrganisationIdOrderByCreeLeDesc(UUID organisationId);

    List<Paiement> findAllByStatutOrderByCreeLeDesc(Paiement.Statut statut);
}
