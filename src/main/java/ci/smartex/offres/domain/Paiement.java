package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Un encaissement : Mobile Money, carte ou virement.
 *
 * <p>La reference est unique en base. C'est ce qui rend la confirmation de
 * l'agregateur idempotente : deux notifications pour le meme paiement ne
 * creditent pas deux fois.
 */
@Entity
@Table(name = "paiement")
@Getter
@Setter
public class Paiement {

    public enum Moyen {
        MOBILE_MONEY,
        CARTE,
        /** Valide a la main par le super-administrateur. */
        VIREMENT
    }

    public enum Statut {
        EN_ATTENTE,
        CONFIRME,
        ECHOUE,
        REMBOURSE
    }

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "organisation_id", columnDefinition = "uuid")
    private UUID organisationId;

    @Column(name = "demande_id", columnDefinition = "uuid")
    private UUID demandeId;

    @Column(name = "abonnement_id", columnDefinition = "uuid")
    private UUID abonnementId;

    @Column(nullable = false)
    private long montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Moyen moyen;

    @Column(nullable = false, unique = true)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.EN_ATTENTE;

    /** Recu PDF genere par Jasper, comme toute autre piece. */
    @Column(name = "recu_chemin")
    private String recuChemin;

    @Column(name = "confirme_le")
    private Instant confirmeLe;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe = Instant.now();
}
