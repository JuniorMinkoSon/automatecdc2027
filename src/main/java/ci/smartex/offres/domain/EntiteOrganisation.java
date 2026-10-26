package ci.smartex.offres.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Toute donnee metier appartient a une organisation et porte sa marque.
 *
 * <p>Cette colonne n'est pas une commodite applicative : c'est elle que lit la
 * Row-Level Security de PostgreSQL. Le code peut se tromper de filtre, la base
 * ne renverra jamais la ligne d'une autre entreprise.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class EntiteOrganisation {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "organisation_id", nullable = false, columnDefinition = "uuid")
    private UUID organisationId;
}
