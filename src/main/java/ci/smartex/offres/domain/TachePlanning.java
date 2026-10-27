package ci.smartex.offres.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Une tache du planning d'execution : quand elle commence, combien elle dure. */
@Entity
@Table(name = "tache_planning")
@Getter
@Setter
public class TachePlanning extends EntiteOrganisation {

    @Column(name = "dossier_id", nullable = false, columnDefinition = "uuid")
    private UUID dossierId;

    @Column(nullable = false)
    private String libelle;

    @Column(name = "debut_mois", nullable = false)
    private int debutMois = 1;

    @Column(name = "duree_mois", nullable = false)
    private int dureeMois = 1;

    @Column(nullable = false)
    private int ordre;

    /** Le mois ou la tache s'acheve, inclus. */
    public int finMois() {
        return debutMois + dureeMois - 1;
    }
}
