package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Espace client. Une organisation, une entreprise soumissionnaire. */
@Entity
@Table(name = "organisation")
@Getter
@Setter
public class Organisation {

    public enum Statut {
        EN_ATTENTE,
        ACTIVE,
        /** Abonnement echu : les dossiers restent telechargeables, rien n'est supprime. */
        LECTURE_SEULE,
        SUSPENDUE
    }

    public enum Mode {
        /** L'entreprise saisit et prepare elle-meme. */
        LIBRE_SERVICE,
        /** L'equipe de la plateforme prepare, l'entreprise valide. */
        CLE_EN_MAIN
    }

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.EN_ATTENTE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode = Mode.LIBRE_SERVICE;

    @Column(name = "quota_dossiers", nullable = false)
    private int quotaDossiers = 50;

    @Column(name = "quota_stockage_mo", nullable = false)
    private int quotaStockageMo = 5000;

    /** Une organisation peut fournir sa propre cle d'API, sinon celle du VPS sert. */
    @Column(name = "cle_api_llm")
    private String cleApiLlm;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe = Instant.now();

    public boolean peutEcrire() {
        return statut == Statut.ACTIVE;
    }
}
