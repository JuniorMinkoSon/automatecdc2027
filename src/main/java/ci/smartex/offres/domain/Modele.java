package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Un modele Jasper, commun a toutes les organisations.
 *
 * <p>Les modeles sont versionnes dans Git et jamais televerses par les clients :
 * c'est ce qui garantit qu'une piece a la meme forme chez tout le monde et que
 * personne ne peut glisser une clause dans un formulaire officiel.
 */
@Entity
@Table(name = "modele")
@Getter
@Setter
public class Modele {

    public enum Offre {
        TECHNIQUE,
        FINANCIERE,
        COMMUN
    }

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String libelle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Offre offre;

    @Column(name = "fichier_jrxml", nullable = false)
    private String fichierJrxml;

    @Column(nullable = false)
    private int version = 1;

    /** Place de la piece dans le sommaire du dossier assemble. */
    @Column(name = "ordre_sommaire", nullable = false)
    private int ordreSommaire;

    @Column(nullable = false)
    private boolean actif = true;
}
