package ci.smartex.offres.repo;

import ci.smartex.offres.domain.Dossier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DepotDossier extends JpaRepository<Dossier, UUID> {

    List<Dossier> findAllByOrganisationIdOrderByMajLeDesc(UUID organisationId);

    List<Dossier> findAllByOrganisationIdAndStatut(UUID organisationId, Dossier.Statut statut);

    List<Dossier> findAllByAppelOffresId(UUID appelOffresId);

    long countByOrganisationIdAndStatutNot(UUID organisationId, Dossier.Statut statut);

    /**
     * Dossiers non deposes dont la date limite approche : ce sont eux qui
     * declenchent les rappels a J-3 et J-1.
     */
    @Query("""
           select d from Dossier d
             join AppelOffres a on a.id = d.appelOffresId
           where d.statut <> :statutExclu
             and a.dateLimiteDepot is not null
             and a.dateLimiteDepot between :debut and :fin
           """)
    List<Dossier> aEcheanceEntre(@Param("debut") Instant debut,
                                 @Param("fin") Instant fin,
                                 @Param("statutExclu") Dossier.Statut statutExclu);
}
