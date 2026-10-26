package ci.smartex.offres.repo;

import ci.smartex.offres.domain.Modele;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepotModele extends JpaRepository<Modele, UUID> {

    Optional<Modele> findFirstByCodeAndActifTrueOrderByVersionDesc(String code);

    List<Modele> findAllByActifTrueOrderByOrdreSommaireAsc();

    List<Modele> findAllByOffreAndActifTrueOrderByOrdreSommaireAsc(Modele.Offre offre);
}
