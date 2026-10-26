package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Une entreprise qui veut un compte.
 *
 * <p>Aucun compte n'est ouvert avant paiement et verification du RCCM par le
 * super-administrateur, quel que soit le mode choisi.
 */
@Entity
@Table(name = "demande_inscription")
@Getter
@Setter
public class DemandeInscription {

    public enum Statut {
        RECUE,
        PAYEE,
        RCCM_A_VERIFIER,
        ACTIVEE,
        REFUSEE
    }

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "raison_sociale", nullable = false)
    private String raisonSociale;

    @Column(nullable = false)
    private String rccm;

    @Column(name = "contact_nom", nullable = false)
    private String contactNom;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_telephone")
    private String contactTelephone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Organisation.Mode mode;

    @Column(nullable = false)
    private String formule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.RECUE;

    /** Renseigne a l'activation, pas avant. */
    @Column(name = "organisation_id", columnDefinition = "uuid")
    private UUID organisationId;

    @Column(name = "motif_refus")
    private String motifRefus;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe = Instant.now();
}
