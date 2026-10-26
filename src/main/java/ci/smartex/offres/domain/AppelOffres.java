package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Un DAO lu par l'OCR et l'IA.
 *
 * <p>Ces champs sont ceux qui changent d'un appel d'offres a l'autre, et qui
 * etaient jusqu'ici recopies a la main dans huit pieces au moins. Ils sont
 * extraits une fois, relus par un humain, puis jamais ressaisis.
 */
@Entity
@Table(name = "appel_offres")
@Getter
@Setter
public class AppelOffres extends EntiteOrganisation {

    public enum Statut {
        EN_ANALYSE,
        /** L'IA a rendu son extraction : elle attend une relecture humaine. */
        A_VERIFIER,
        VERIFIE,
        ECHEC_ANALYSE
    }

    @Column(name = "numero_ao")
    private String numeroAo;

    @Column(nullable = false)
    private String intitule;

    @Column(name = "autorite_contractante")
    private String autoriteContractante;

    @Column(name = "maitre_ouvrage")
    private String maitreOuvrage;

    @Column(name = "maitre_oeuvre")
    private String maitreOeuvre;

    /** A qui la soumission est adressee (IC 1.1). */
    private String destinataire;

    private String financement;

    /** IC 19.1 : 120 jours dans le DAO de la Bagoue. */
    @Column(name = "validite_offre_jours")
    private Integer validiteOffreJours;

    /** IC 14.5 et 15.1 : « TTC, fermes et non revisables ». */
    @Column(name = "regime_prix")
    private String regimePrix;

    /**
     * Nul tant que la question n'est pas tranchee. Le DAO exige des prix TTC
     * alors que la TVA est « non prise en compte » : la plateforme avertit au
     * lieu de choisir a la place du soumissionnaire.
     */
    @Column(name = "tva_applicable")
    private Boolean tvaApplicable;

    @Column(name = "date_limite_depot")
    private Instant dateLimiteDepot;

    @Column(name = "timbre_fiscal")
    private Long timbreFiscal;

    @Column(name = "dao_chemin")
    private String daoChemin;

    /** L'extraction brute de l'IA, conservee pour pouvoir justifier chaque champ. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extraction_json")
    private String extractionJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.EN_ANALYSE;

    @Column(name = "erreur_analyse")
    private String erreurAnalyse;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe = Instant.now();

    public UUID identifiant() {
        return getId();
    }
}
