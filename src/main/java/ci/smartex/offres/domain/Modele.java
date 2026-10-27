package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Une piece du dossier et la facon de la produire.
 *
 * <p>Trois provenances coexistent. Les rapports Jasper sont versionnes dans
 * Git, communs a toutes les organisations : ce sont les formulaires imposes
 * par les bailleurs, et personne ne peut y glisser une clause. Les modeles
 * Word sont televerses par une entreprise et lui appartiennent : ce sont ses
 * propres documents, mis au point au fil des marches, qu'on remplit en
 * conservant leur mise en page plutot que de les reconstruire. Les pieces
 * jointes viennent du coffre et partent telles quelles.
 *
 * <p>Un modele d'entreprise peut porter le meme code qu'un modele commun :
 * c'est ainsi qu'une entreprise substitue sa lettre type a celle livree, sans
 * priver les autres de la version d'origine.
 */
@Entity
@Table(name = "modele")
@Getter
@Setter
public class Modele {

    public enum Offre {
        TECHNIQUE,
        FINANCIERE,
        COMMUN
    }

    /** Comment la piece est produite. */
    public enum Format {
        /** Rapport livre avec l'application, commun a toutes les organisations. */
        JASPER,
        /** Modele Word televerse par l'entreprise, rempli en conservant sa mise en page. */
        DOCX,
        /** Piece justificative du coffre, jointe telle quelle. */
        PIECE_JOINTE
    }

    /** A quoi la piece sert dans le dossier, pour la classer au sommaire. */
    public enum Categorie {
        ADMINISTRATIF,
        ENGAGEMENT,
        RENSEIGNEMENT,
        MOYENS,
        FINANCIER,
        METHODOLOGIE,
        AUTRE
    }

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String libelle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Offre offre;

    /**
     * Nulle pour un modele commun a toutes les organisations, renseignee pour
     * un modele televerse par une entreprise. C'est ainsi qu'une entreprise
     * remplace une lettre type livree par la sienne, sans priver les autres
     * de la version d'origine.
     */
    @Column(name = "organisation_id", columnDefinition = "uuid")
    private UUID organisationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Format format = Format.JASPER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Categorie categorie = Categorie.AUTRE;

    /** Le .jrxml, pour un modele Jasper. */
    @Column(name = "fichier_jrxml")
    private String fichierJrxml;

    /** Le document televerse, pour un modele Word ou une piece jointe. */
    @Column(name = "fichier_chemin")
    private String fichierChemin;

    /**
     * Les champs trouves dans le document, qui disent au formulaire quoi
     * demander. Lus du modele au televersement, jamais codes en dur.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "champs_detectes")
    private String champsDetectes;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe = Instant.now();

    @Column(nullable = false)
    private int version = 1;

    /** Place de la piece dans le sommaire du dossier assemble. */
    @Column(name = "ordre_sommaire", nullable = false)
    private int ordreSommaire;

    @Column(nullable = false)
    private boolean actif = true;

    public boolean estTeleverse() {
        return organisationId != null;
    }

    public boolean seRemplit() {
        return format == Format.JASPER || format == Format.DOCX;
    }
}
