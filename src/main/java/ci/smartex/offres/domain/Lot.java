package ci.smartex.offres.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Un lot d'un appel d'offres : son delai et sa garantie lui sont propres. */
@Entity
@Table(name = "lot")
@Getter
@Setter
public class Lot extends EntiteOrganisation {

    @Column(name = "appel_offres_id", nullable = false, columnDefinition = "uuid")
    private UUID appelOffresId;

    @Column(nullable = false)
    private int numero;

    @Column(nullable = false)
    private String intitule;

    private String localite;

    /** IC 13.2 : 4 mois maximum dans le DAO de la Bagoue. */
    @Column(name = "delai_mois")
    private Integer delaiMois;

    /** IC 20.2 : 850 000 F CFA par lot. */
    @Column(name = "montant_garantie")
    private Long montantGarantie;
}
