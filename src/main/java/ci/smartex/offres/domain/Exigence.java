package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Un critere extrait du DAO, que le dossier devra satisfaire.
 *
 * <p>{@code eliminatoire} distingue ce qui fait rejeter l'offre (garantie,
 * quitus ARCOP) de ce qui la note seulement.
 */
@Entity
@Table(name = "exigence")
@Getter
@Setter
public class Exigence extends EntiteOrganisation {

    public enum Type {
        PERSONNEL,
        MATERIEL,
        CHIFFRE_AFFAIRES,
        EXPERIENCE,
        PIECE,
        AUTRE
    }

    @Column(name = "appel_offres_id", nullable = false, columnDefinition = "uuid")
    private UUID appelOffresId;

    /** Nul si l'exigence vaut pour tous les lots. */
    @Column(name = "lot_id", columnDefinition = "uuid")
    private UUID lotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private String libelle;

    private String valeur;

    /** Par exemple : une betonniere par lot. */
    @Column(name = "quantite_min")
    private Integer quantiteMin;

    /** Par exemple : cinq ans pour un conducteur des travaux. */
    @Column(name = "annees_min")
    private Integer anneesMin;

    @Column(nullable = false)
    private boolean eliminatoire = false;

    /** La clause du DAO qui porte l'exigence, pour pouvoir la citer a l'ecran. */
    @Column(name = "source_dao")
    private String sourceDao;
}
