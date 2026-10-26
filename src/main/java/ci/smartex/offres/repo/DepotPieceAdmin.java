package ci.smartex.offres.repo;

import ci.smartex.offres.domain.PieceAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepotPieceAdmin extends JpaRepository<PieceAdmin, UUID> {

    List<PieceAdmin> findAllByOrganisationId(UUID organisationId);

    /** La piece la plus recemment emise pour un type donne : c'est celle qui sert. */
    Optional<PieceAdmin> findFirstByOrganisationIdAndTypeAndConfirmeeTrueOrderByDateEmissionDesc(
            UUID organisationId, PieceAdmin.Type type);

    /**
     * Pieces qui expirent dans la fenetre surveillee par la veille quotidienne.
     * Requete non filtree par organisation : la tache la relance organisation
     * par organisation, chacune dans son propre contexte RLS.
     */
    @Query("""
           select p from PieceAdmin p
           where p.dateExpiration is not null
             and p.dateExpiration between :debut and :fin
           """)
    List<PieceAdmin> expirantEntre(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);
}
