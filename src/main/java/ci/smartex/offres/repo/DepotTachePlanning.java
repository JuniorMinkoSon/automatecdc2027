package ci.smartex.offres.repo;

import ci.smartex.offres.domain.TachePlanning;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepotTachePlanning extends JpaRepository<TachePlanning, UUID> {

    List<TachePlanning> findAllByDossierIdOrderByOrdreAsc(UUID dossierId);

    void deleteAllByDossierId(UUID dossierId);
}
