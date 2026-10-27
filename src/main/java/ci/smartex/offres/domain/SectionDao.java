package ci.smartex.offres.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Une section d'un dossier d'appel d'offres publie.
 *
 * <p>Le code des marches distingue ce qui ne se modifie pas - Instructions aux
 * candidats, Cahier des clauses administratives generales - de ce qui est
 * propre a chaque marche. Les premieres sont livrees avec la plateforme et
 * verrouillees : une collectivite qui y glisserait une clause produirait un
 * dossier attaquable, et c'est elle qui en repondrait. Les secondes se
 * redigent, et c'est la que le temps se perd aujourd'hui.
 */
@Entity
@Table(name = "section_dao")
@Getter
@Setter
public class SectionDao extends EntiteOrganisation {

    @Column(name = "appel_offres_id", nullable = false, columnDefinition = "uuid")
    private UUID appelOffresId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String intitule;

    @Column(nullable = false)
    private int ordre;

    /** Faux pour les sections que le code des marches fige. */
    @Column(nullable = false)
    private boolean modifiable = true;

    @Column
    private String contenu;

    /** Un modele televerse peut fournir la section, pour un cahier technique deja ecrit. */
    @Column(name = "modele_id", columnDefinition = "uuid")
    private UUID modeleId;

    @Column(name = "maj_le", nullable = false)
    private Instant majLe = Instant.now();

    public boolean estVide() {
        return (contenu == null || contenu.isBlank()) && modeleId == null;
    }
}
