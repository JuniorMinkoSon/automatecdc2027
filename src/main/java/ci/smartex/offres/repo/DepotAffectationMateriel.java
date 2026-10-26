package ci.smartex.offres.repo;

import ci.smartex.offres.domain.AffectationMateriel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepotAffectationMateriel extends JpaRepository<AffectationMateriel, UUID> {

    List<AffectationMateriel> findAllByDossierId(UUID dossierId);

    void deleteAllByDossierId(UUID dossierId);
}
