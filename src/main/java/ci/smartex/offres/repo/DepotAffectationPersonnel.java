package ci.smartex.offres.repo;

import ci.smartex.offres.domain.AffectationPersonnel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepotAffectationPersonnel extends JpaRepository<AffectationPersonnel, UUID> {

    List<AffectationPersonnel> findAllByDossierId(UUID dossierId);

    void deleteAllByDossierId(UUID dossierId);
}
