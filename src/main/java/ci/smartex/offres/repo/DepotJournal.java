package ci.smartex.offres.repo;

import ci.smartex.offres.domain.Journal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DepotJournal extends JpaRepository<Journal, Long> {

    Page<Journal> findAllByOrganisationIdOrderBySurvenuLeDesc(UUID organisationId, Pageable page);
}
