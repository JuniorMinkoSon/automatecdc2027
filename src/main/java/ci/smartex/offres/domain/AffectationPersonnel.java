package ci.smartex.offres.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Qui le dossier engage face a une exigence de personnel du DAO. */
@Entity
@Table(name = "affectation_personnel")
@Getter
@Setter
public class AffectationPersonnel extends EntiteOrganisation {

    @Column(name = "dossier_id", nullable = false, columnDefinition = "uuid")
    private UUID dossierId;

    @Column(name = "personnel_id", nullable = false, columnDefinition = "uuid")
    private UUID personnelId;

    @Column(name = "exigence_id", columnDefinition = "uuid")
    private UUID exigenceId;

    /** Le poste tel que le DAO le nomme, par exemple conducteur des travaux. */
    @Column(name = "poste_exige", nullable = false)
    private String posteExige;

    /** Faux des que l'utilisateur a corrige la proposition du rapprochement. */
    @Column(name = "propose_automatiquement", nullable = false)
    private boolean proposeAutomatiquement = true;
}
