package ci.smartex.offres.repo;

import ci.smartex.offres.domain.LigneDqe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepotLigneDqe extends JpaRepository<LigneDqe, UUID> {

    List<LigneDqe> findAllByDossierIdOrderByOrdreSectionAscOrdreAsc(UUID dossierId);

    long countByDossierIdAndPrixUnitaireIsNull(UUID dossierId);

    void deleteAllByDossierId(UUID dossierId);
}
