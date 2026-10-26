package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * L'abonnement d'une organisation.
 *
 * <p>A l'expiration, l'acces passe en lecture seule : les dossiers restent
 * telechargeables. On ne supprime pas le travail d'une entreprise parce
 * qu'elle a laisse passer une echeance.
 */
@Entity
@Table(name = "abonnement")
@Getter
@Setter
public class Abonnement extends EntiteOrganisation {

    public enum Statut {
        ACTIF,
        EXPIRE,
        RESILIE
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Organisation.Mode mode;

    @Column(nullable = false)
    private String formule;

    @Column(nullable = false)
    private LocalDate debut;

    @Column(nullable = false)
    private LocalDate fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.ACTIF;

    @Column(name = "rappel_j15_envoye", nullable = false)
    private boolean rappelJ15Envoye = false;

    @Column(name = "rappel_j3_envoye", nullable = false)
    private boolean rappelJ3Envoye = false;

    public boolean estEchu(LocalDate aujourdhui) {
        return fin.isBefore(aujourdhui);
    }

    public long joursRestants(LocalDate aujourdhui) {
        return ChronoUnit.DAYS.between(aujourdhui, fin);
    }
}
