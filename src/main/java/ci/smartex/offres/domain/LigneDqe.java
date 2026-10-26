package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Une ligne du detail quantitatif et estimatif.
 *
 * <p>Designation, unite et quantite viennent du DAO et sont verrouillees ;
 * l'utilisateur ne tape que le prix unitaire. Le montant n'a pas de setter :
 * c'est une colonne generee par PostgreSQL, relue apres chaque ecriture. Une
 * erreur arithmetique dans un DQE n'est donc pas rattrapee, elle est
 * impossible, ce qui compte puisque la COJO corrige ces erreurs elle-meme
 * (IC 31.3) et que la correction change le classement.
 */
@Entity
@Table(name = "ligne_dqe")
@Getter
@Setter
public class LigneDqe extends EntiteOrganisation {

    @Column(name = "dossier_id", nullable = false, columnDefinition = "uuid")
    private UUID dossierId;

    /** Par exemple « I - TERRASSEMENTS ET MOUVEMENT DE TERRES ». */
    @Column(nullable = false)
    private String section;

    @Column(name = "ordre_section", nullable = false)
    private int ordreSection;

    @Column(name = "numero_prix", nullable = false)
    private String numeroPrix;

    @Column(nullable = false)
    private int ordre;

    private String designation;

    /** m2, m3, forfait : une unite fausse au DQE se voit a la lecture. */
    private String unite;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantite = BigDecimal.ZERO;

    /** Le seul champ que l'utilisateur saisit. Nul tant qu'il ne l'a pas fait. */
    @Column(name = "prix_unitaire")
    private Long prixUnitaire;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Setter(AccessLevel.NONE)
    @Column(name = "montant")
    private Long montant;

    /** Faux pour une ligne ajoutee a la main, que le controle doit signaler. */
    @Column(name = "issue_du_dao", nullable = false)
    private boolean issueDuDao = true;

    public boolean prixManquant() {
        return prixUnitaire == null;
    }

    public boolean designationManquante() {
        return designation == null || designation.isBlank();
    }

    public long montantOuZero() {
        return montant == null ? 0L : montant;
    }
}
