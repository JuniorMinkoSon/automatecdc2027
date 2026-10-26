package ci.smartex.offres.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Quel materiel le dossier engage face a une exigence du DAO. */
@Entity
@Table(name = "affectation_materiel")
@Getter
@Setter
public class AffectationMateriel extends EntiteOrganisation {

    @Column(name = "dossier_id", nullable = false, columnDefinition = "uuid")
    private UUID dossierId;

    @Column(name = "materiel_id", nullable = false, columnDefinition = "uuid")
    private UUID materielId;

    @Column(name = "exigence_id", columnDefinition = "uuid")
    private UUID exigenceId;

    @Column(name = "designation_exigee", nullable = false)
    private String designationExigee;

    @Column(name = "propose_automatiquement", nullable = false)
    private boolean proposeAutomatiquement = true;
}
