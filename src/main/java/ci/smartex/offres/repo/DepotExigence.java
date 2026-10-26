package ci.smartex.offres.repo;

import ci.smartex.offres.domain.Exigence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepotExigence extends JpaRepository<Exigence, UUID> {

    List<Exigence> findAllByAppelOffresId(UUID appelOffresId);

    List<Exigence> findAllByAppelOffresIdAndType(UUID appelOffresId, Exigence.Type type);

    void deleteAllByAppelOffresId(UUID appelOffresId);
}
