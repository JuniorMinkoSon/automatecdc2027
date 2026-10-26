package ci.smartex.offres.repo;

import ci.smartex.offres.domain.DocumentGenere;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepotDocumentGenere extends JpaRepository<DocumentGenere, UUID> {

    List<DocumentGenere> findAllByDossierIdOrderByGenereLeDesc(UUID dossierId);

    Optional<DocumentGenere> findFirstByDossierIdAndTypeAndFormatOrderByVersionDesc(
            UUID dossierId, DocumentGenere.Type type, DocumentGenere.Format format);
}
