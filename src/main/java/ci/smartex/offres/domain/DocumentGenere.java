package ci.smartex.offres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Un fichier produit par Jasper.
 *
 * <p>L'empreinte SHA-256 est ce qui permet de prouver, plus tard, que le PDF
 * imprime est bien celui qui a ete valide.
 */
@Entity
@Table(name = "document_genere")
@Getter
@Setter
public class DocumentGenere extends EntiteOrganisation {

    public enum Type {
        TECHNIQUE,
        FINANCIERE,
        RAPPORT_CONFORMITE,
        RECU
    }

    public enum Format {
        /** Le seul livrable officiel. */
        PDF,
        /** Relecture de brouillon seulement : on ne corrige jamais dans le Word. */
        DOCX
    }

    @Column(name = "dossier_id", nullable = false, columnDefinition = "uuid")
    private UUID dossierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Format format;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private boolean brouillon = true;

    @Column(name = "empreinte_sha256", nullable = false)
    private String empreinteSha256;

    @Column(nullable = false)
    private String chemin;

    @Column(name = "taille_octets")
    private Long tailleOctets;

    @Column(name = "genere_le", nullable = false)
    private Instant genereLe = Instant.now();
}
