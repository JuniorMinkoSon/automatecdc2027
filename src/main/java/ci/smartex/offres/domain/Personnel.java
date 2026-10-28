package ci.smartex.offres.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Le personnel de l'entreprise, rapproche des exigences du DAO.
 *
 * <p>Le DAO de reference exige un conducteur des travaux (BTS batiment, 5 ans)
 * et un chef de chantier (BT batiment, 3 ans) par lot, distincts entre lots.
 * Le formulaire PER-1 de l'offre deposee etait vide : le rapprochement
 * automatique et le controle bloquant existent pour cela.
 */
@Entity
@Table(name = "personnel")
@Getter
@Setter
public class Personnel extends EntiteOrganisation {

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String poste;

    private String diplome;

    @Column(name = "annees_experience", nullable = false)
    private int anneesExperience;

    private String projets;

    @Column(name = "cv_chemin")
    private String cvChemin;

    @Column(name = "diplome_chemin")
    private String diplomeChemin;
}
