package ci.smartex.offres.repo;

import ci.smartex.offres.domain.DonneeDpao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepotDonneeDpao extends JpaRepository<DonneeDpao, UUID> {

    List<DonneeDpao> findAllByAppelOffresIdOrderByOrdreAsc(UUID appelOffresId);

    Optional<DonneeDpao> findByAppelOffresIdAndReferenceIc(UUID appelOffresId, String referenceIc);
}
