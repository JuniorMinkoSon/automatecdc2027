package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Le parc materiel, rapproche du materiel minimum exige par lot. */
@Entity
@Table(name = "materiel")
@Getter
@Setter
public class Materiel extends EntiteOrganisation {

    public enum Provenance {
        POSSESSION,
        LOCATION
    }

    @Column(nullable = false)
    private String designation;

    private String marque;

    private String capacite;

    private Integer annee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provenance provenance = Provenance.POSSESSION;

    /** Carte grise, facture ou contrat de location : le DAO exige un justificatif. */
    @Column(name = "justificatif_chemin")
    private String justificatifChemin;
}
