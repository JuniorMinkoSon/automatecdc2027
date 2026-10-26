package ci.smartex.offres.repo;

import ci.smartex.offres.domain.Lot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepotLot extends JpaRepository<Lot, UUID> {

    List<Lot> findAllByAppelOffresIdOrderByNumeroAsc(UUID appelOffresId);

    void deleteAllByAppelOffresId(UUID appelOffresId);
}
