package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Le resultat d'un controle sur un dossier, archive avec lui.
 *
 * <p>Un controle bloquant qui echoue interdit la validation. Un avertissement
 * s'affiche et laisse la decision au validateur : la plateforme ne tranche pas
 * a sa place sur ce qu'elle ne sait pas trancher, comme le regime de TVA.
 */
@Entity
@Table(name = "controle_resultat")
@Getter
@Setter
public class ControleResultat extends EntiteOrganisation {

    public enum Niveau {
        BLOQUANT,
        AVERTISSEMENT
    }

    @Column(name = "dossier_id", nullable = false, columnDefinition = "uuid")
    private UUID dossierId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String libelle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Niveau niveau;

    @Column(nullable = false)
    private boolean reussi;

    private String message;

    /** Ou aller corriger : le coffre, le personnel ou les prix. */
    @Column(name = "lien_correction")
    private String lienCorrection;

    @Column(name = "execute_le", nullable = false)
    private Instant executeLe = Instant.now();

    public boolean bloque() {
        return !reussi && niveau == Niveau.BLOQUANT;
    }
}
