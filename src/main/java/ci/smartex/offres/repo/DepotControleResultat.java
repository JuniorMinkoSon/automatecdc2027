package ci.smartex.offres.repo;

import ci.smartex.offres.domain.ControleResultat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepotControleResultat extends JpaRepository<ControleResultat, UUID> {

    List<ControleResultat> findAllByDossierIdOrderByNiveauAscCodeAsc(UUID dossierId);

    void deleteAllByDossierId(UUID dossierId);
}
