package ci.smartex.offres.repo;

import ci.smartex.offres.domain.SectionDao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepotSectionDao extends JpaRepository<SectionDao, UUID> {

    List<SectionDao> findAllByAppelOffresIdOrderByOrdreAsc(UUID appelOffresId);

    Optional<SectionDao> findByAppelOffresIdAndCode(UUID appelOffresId, String code);

    void deleteAllByAppelOffresId(UUID appelOffresId);
}
