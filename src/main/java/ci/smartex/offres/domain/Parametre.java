package ci.smartex.offres.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Reglages propres a une organisation : validateurs, destinataires d'alertes. */
@Entity
@Table(name = "parametre")
@Getter
@Setter
public class Parametre extends EntiteOrganisation {

    @Column(nullable = false)
    private String cle;

    private String valeur;
}
