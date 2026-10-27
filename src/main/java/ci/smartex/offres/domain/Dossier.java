package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Une reponse a un appel d'offres, pour un lot.
 *
 * <p>Quatre statuts, une seule boucle de correction. Tant que le dossier n'est
 * pas valide, chaque page porte le filigrane BROUILLON. La validation fige la
 * version ; toute modification ulterieure cree une version n+1 plutot que de
 * reecrire celle qui a ete signee.
 */
@Entity
@Table(name = "dossier")
@Getter
@Setter
public class Dossier extends EntiteOrganisation {

    public enum Statut {
        BROUILLON,
        EN_VALIDATION,
        VALIDE,
        DEPOSE;

        public boolean estFige() {
            return this == VALIDE || this == DEPOSE;
        }
    }

    @Column(name = "appel_offres_id", nullable = false, columnDefinition = "uuid")
    private UUID appelOffresId;

    @Column(name = "lot_id", columnDefinition = "uuid")
    private UUID lotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.BROUILLON;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "representant_id", columnDefinition = "uuid")
    private UUID representantId;

    /** Ce que l'entreprise s'engage a tenir ; compare au delai du lot. */
    @Column(name = "delai_execution_mois")
    private Integer delaiExecutionMois;

    @Column(name = "validite_offre_jours")
    private Integer validiteOffreJours;

    @Column(name = "validite_garantie_jours")
    private Integer validiteGarantieJours;

    /**
     * Le montant que portera la lettre de soumission. Fige par le service de
     * chiffrage, jamais saisi. Le controle MONTANT_EN_LETTRES le compare a la
     * somme recalculee des lignes : s'ils divergent, la lettre est perimee.
     */
    @Column(name = "montant_soumission")
    private Long montantSoumission;

    /**
     * Declaration du soumissionnaire sur les marches non executes.
     *
     * <p>La plateforme ne peut pas la deduire : un marche inexecute ne laisse
     * pas de trace chez celui qui ne l'a pas execute. C'est donc une
     * declaration, et le formulaire n'en imprime qu'une des deux - les deux
     * ensemble laisseraient croire qu'une case a ete cochee par inadvertance.
     */
    @Column(name = "sans_antecedent", nullable = false)
    private boolean sansAntecedent = true;

    @Column(name = "antecedents_detail")
    private String antecedentsDetail;

    @Column(name = "auteur_id", columnDefinition = "uuid")
    private UUID auteurId;

    @Column(name = "validateur_id", columnDefinition = "uuid")
    private UUID validateurId;

    /**
     * Qui a touche au dossier en dernier. La separation des taches se joue ici :
     * celui qui a modifie ne peut pas valider.
     */
    @Column(name = "modifie_par", columnDefinition = "uuid")
    private UUID modifiePar;

    @Column(name = "commentaire_validation")
    private String commentaireValidation;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe = Instant.now();

    @Column(name = "maj_le", nullable = false)
    private Instant majLe = Instant.now();

    @Column(name = "valide_le")
    private Instant valideLe;

    @Column(name = "depose_le")
    private Instant deposeLe;

    public boolean estModifiable() {
        return statut == Statut.BROUILLON;
    }

    /** Le filigrane tombe a la validation, et seulement a la validation. */
    public boolean porteFiligrane() {
        return !statut.estFige();
    }
}
