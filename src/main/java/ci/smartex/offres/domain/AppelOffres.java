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

    /**
     * De quel cote du marche on se trouve.
     *
     * <p>Le meme objet sert aux deux : l'intitule, les lots, les delais et les
     * garanties sont les memes, seul change qui les ecrit. Les dupliquer dans
     * deux tables aurait garanti qu'ils divergent.
     */
    public enum Role {
        /** Marche publie par un tiers, auquel l'organisation repond. */
        REPONSE,
        /** Marche que l'organisation publie elle-meme. */
        PUBLICATION
    }

    public enum Statut {
        EN_ANALYSE,
        /** L'IA a rendu son extraction : elle attend une relecture humaine. */
        A_VERIFIER,
        VERIFIE,
        ECHEC_ANALYSE
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.REPONSE;

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

    // ---------------------------------------------------- cote autorite contractante
    //
    // Tous nuls pour un appel d'offres auquel on se contente de repondre.

    /** Imputation budgetaire, annoncee dans l'avis. */
    @Column(name = "ligne_budgetaire")
    private String ligneBudgetaire;

    /** Appel d'offres ouvert, restreint, consultation : la forme fixe le reste. */
    @Column(name = "mode_passation")
    private String modePassation;

    /** Prix de retrait du dossier, non remboursable. */
    @Column(name = "prix_dossier")
    private Long prixDossier;

    @Column(name = "moyen_paiement_dossier")
    private String moyenPaiementDossier;

    @Column(name = "adresse_retrait")
    private String adresseRetrait;

    @Column(name = "plateforme_depot")
    private String plateformeDepot;

    @Column(name = "url_depot")
    private String urlDepot;

    @Column(name = "ouverture_plis")
    private Instant ouverturePlis;

    @Column(name = "lieu_ouverture")
    private String lieuOuverture;

    @Column(name = "contacts")
    private String contacts;

    @Column(name = "qualifications_exigees")
    private String qualificationsExigees;

    /**
     * La duree de validite annoncee dans l'avis.
     *
     * <p>Distincte de {@code validiteOffreJours}, qui est celle du reglement.
     * Quand les deux different, le candidat redige une offre non conforme sans
     * avoir commis d'erreur : c'est le dossier qui se contredit, et c'est
     * l'autorite contractante qui doit trancher avant de publier.
     */
    @Column(name = "validite_annoncee_jours")
    private Integer validiteAnnonceeJours;

    /** Redevance de regulation, en pourcentage du montant hors taxes. */
    @Column(name = "redevance_arcop")
    private java.math.BigDecimal redevanceArcop;

    @Column(name = "reference_code_marches")
    private String referenceCodeMarches;

    public boolean estPublie() {
        return role == Role.PUBLICATION;
    }

    public UUID identifiant() {
        return getId();
    }
}
