package ci.smartex.offres.repo;

import ci.smartex.offres.domain.DemandeInscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepotDemandeInscription extends JpaRepository<DemandeInscription, UUID> {

    List<DemandeInscription> findAllByStatutOrderByCreeLeAsc(DemandeInscription.Statut statut);

    List<DemandeInscription> findAllByOrderByCreeLeDesc();
}
