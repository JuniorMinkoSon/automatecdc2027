package ci.smartex.offres.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Marches deja executes, pour le critere d'experience du DAO. */
@Entity
@Table(name = "reference_marche")
@Getter
@Setter
public class ReferenceMarche extends EntiteOrganisation {

    @Column(name = "maitre_ouvrage", nullable = false)
    private String maitreOuvrage;

    @Column(nullable = false)
    private String objet;

    /** En francs CFA entiers, comme tout montant de la plateforme. */
    private Long montant;

    private Integer annee;

    /** Attestation de bonne execution ou proces-verbal de reception. */
    @Column(name = "justificatif_chemin")
    private String justificatifChemin;
}
