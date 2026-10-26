package ci.smartex.offres.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Le mandat qu'une entreprise donne a l'equipe de la plateforme.
 *
 * <p>Sans mandat en cours, l'operateur mandate ne voit rien de l'organisation.
 * L'entreprise peut le retirer a tout moment, et c'est elle, jamais l'equipe,
 * qui valide la version finale, parce que c'est elle qui signe l'offre.
 */
@Entity
@Table(name = "mandat")
@Getter
@Setter
public class Mandat extends EntiteOrganisation {

    @Column(name = "accorde_le", nullable = false)
    private Instant accordeLe = Instant.now();

    @Column(name = "accorde_par", columnDefinition = "uuid")
    private UUID accordePar;

    @Column(name = "retire_le")
    private Instant retireLe;

    @Column(name = "retire_par", columnDefinition = "uuid")
    private UUID retirePar;

    public boolean estEnCours() {
        return retireLe == null;
    }
}
