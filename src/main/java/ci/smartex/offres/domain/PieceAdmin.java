package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Le coffre de pieces de l'entreprise.
 *
 * <p>Chaque piece porte sa date d'expiration, lue par l'OCR et confirmee en un
 * clic. Une piece expiree ne peut plus entrer dans un dossier : c'est un
 * controle bloquant, pas un avertissement, parce qu'un quitus ARCOP perime
 * elimine l'offre.
 */
@Entity
@Table(name = "piece_admin")
@Getter
@Setter
public class PieceAdmin extends EntiteOrganisation {

    public enum Type {
        RCCM,
        DFE,
        ATTESTATION_PME,
        QUITUS_ARCOP,
        ATTESTATION_BANCAIRE,
        ATTESTATION_FISCALE,
        CNPS,
        CAUTIONNEMENT,
        AUTRE
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    private String libelle;

    private String numero;

    /**
     * Montant porte par la piece, quand elle en porte un : le cautionnement.
     * Nul si l'OCR ne l'a pas lu avec certitude, auquel cas le controle avertit
     * au lieu de bloquer sur un chiffre invente.
     */
    private Long montant;

    @Column(name = "fichier_chemin", nullable = false)
    private String fichierChemin;

    @Column(name = "date_emission")
    private LocalDate dateEmission;

    @Column(name = "date_expiration")
    private LocalDate dateExpiration;

    /** Tant que l'utilisateur n'a pas confirme la lecture de l'IA, la piece ne sert pas. */
    @Column(nullable = false)
    private boolean confirmee = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extraction_json")
    private String extractionJson;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe = Instant.now();

    /** Valable a la date consideree, qui est la date limite de depot, pas aujourd'hui. */
    public boolean valableLe(LocalDate date) {
        if (!confirmee) {
            return false;
        }
        return dateExpiration == null || !dateExpiration.isBefore(date);
    }

    public boolean expireAvant(LocalDate date) {
        return dateExpiration != null && dateExpiration.isBefore(date);
    }
}
