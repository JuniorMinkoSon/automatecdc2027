package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Journal des actions sensibles : qui, quoi, quand, depuis quelle adresse.
 *
 * <p>En ajout seul. Ni l'application ni la migration ne peuvent modifier ou
 * supprimer une ligne : un declencheur PostgreSQL leve une exception, et le
 * droit d'UPDATE est retire au compte applicatif. Un journal qu'on peut
 * reecrire ne prouve rien.
 */
@Entity
@Table(name = "journal")
@Getter
@Setter
public class Journal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", columnDefinition = "uuid")
    private UUID organisationId;

    @Column(name = "utilisateur_id", columnDefinition = "uuid")
    private UUID utilisateurId;

    @Column(nullable = false)
    private String action;

    @Column(name = "objet_type")
    private String objetType;

    @Column(name = "objet_id")
    private String objetId;

    private String detail;

    @Column(name = "adresse_ip")
    private String adresseIp;

    @Column(name = "survenu_le", nullable = false)
    private Instant survenuLe = Instant.now();
}
