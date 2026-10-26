package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Fiche du soumissionnaire, saisie une seule fois.
 *
 * <p>C'est la source unique de la raison sociale, du sigle, du RCCM et du pied
 * de page de toutes les pieces. Les offres actuelles ecrivent « NCHTP » sur
 * l'une et « NOUVEAU CHALLENGE HYDRO TP (E.N.C.H.TP) » sur l'autre ; ici les
 * deux viennent du meme champ, donc l'ecart est impossible.
 */
@Entity
@Table(name = "entreprise")
@Getter
@Setter
public class Entreprise extends EntiteOrganisation {

    @Column(name = "raison_sociale", nullable = false)
    private String raisonSociale;

    private String sigle;

    private String rccm;

    @Column(name = "compte_contribuable")
    private String compteContribuable;

    @Column(name = "regime_fiscal")
    private String regimeFiscal;

    private String adresse;

    @Column(name = "boite_postale")
    private String boitePostale;

    private String telephones;

    private String email;

    @Column(name = "logo_chemin")
    private String logoChemin;

    @Column(name = "entete_chemin")
    private String enteteChemin;

    @Column(name = "pied_de_page")
    private String piedDePage;

    @Column(name = "maj_le", nullable = false)
    private Instant majLe = Instant.now();

    /** Ce qui doit apparaitre en tete des pieces : « NCHTP » si un sigle existe. */
    public String denominationCourte() {
        return (sigle != null && !sigle.isBlank()) ? sigle : raisonSociale;
    }
}
