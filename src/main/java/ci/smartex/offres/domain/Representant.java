package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Le gerant qui signe. Sa signature et son cachet alimentent les lettres types. */
@Entity
@Table(name = "representant")
@Getter
@Setter
public class Representant extends EntiteOrganisation {

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String fonction;

    @Column(name = "signature_chemin")
    private String signatureChemin;

    @Column(name = "cachet_chemin")
    private String cachetChemin;

    @Column(name = "signataire_defaut", nullable = false)
    private boolean signataireDefaut = false;
}
