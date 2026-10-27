package ci.smartex.offres.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Une donnee particuliere de l'appel d'offres.
 *
 * <p>Le DPAO n'est pas de la prose : c'est une suite de reponses numerotees,
 * chacune rattachee a un article des instructions aux candidats. Le saisir
 * dans une zone de texte libre reviendrait a retaper un formulaire, c'est-a-
 * dire a reproduire ce que la plateforme sert a supprimer.
 */
@Entity
@Table(name = "donnee_dpao")
@Getter
@Setter
public class DonneeDpao extends EntiteOrganisation {

    @Column(name = "appel_offres_id", nullable = false, columnDefinition = "uuid")
    private UUID appelOffresId;

    /** L'article des instructions aux candidats auquel la donnee repond. */
    @Column(name = "reference_ic", nullable = false)
    private String referenceIc;

    @Column(nullable = false)
    private String libelle;

    @Column
    private String valeur;

    @Column(nullable = false)
    private int ordre;

    /**
     * Vrai quand la valeur se deduit du marche lui-meme : intitule, lots,
     * delais, garanties. La recopier ici la ferait diverger au premier
     * changement, elle est donc calculee a la generation.
     */
    @Column(nullable = false)
    private boolean derivee = false;

    public boolean aRemplir() {
        return !derivee && (valeur == null || valeur.isBlank());
    }
}
