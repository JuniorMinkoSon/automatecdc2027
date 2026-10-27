package ci.smartex.offres.rendu;

import ci.smartex.offres.domain.DocumentGenere;
import ci.smartex.offres.domain.Dossier;
import ci.smartex.offres.domain.Modele;
import ci.smartex.offres.repo.DepotDocumentGenere;
import ci.smartex.offres.repo.DepotDossier;
import ci.smartex.offres.service.ServiceStockage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Assemble le dossier en une archive telechargeable.
 *
 * <p>Les pieces sont numerotees dans l'ordre du sommaire, pour que le dossier
 * imprime suive le meme ordre que celui annonce. Les documents Word restent au
 * format Word : c'est ce qui permet de corriger une tournure avant signature,
 * et refuser cette possibilite pousserait a retaper le document ailleurs, ce
 * qui est exactement le travers qu'on cherche a supprimer.
 *
 * <p>Un fichier « A COMPLETER » accompagne toujours l'archive quand il manque
 * quelque chose. Un dossier incomplet doit se declarer incomplet : la
 * commission, elle, ne previent pas.
 */
@Service
public class ServiceArchiveDossier {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceArchiveDossier.class);
    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final DepotDossier depotDossier;
    private final DepotDocumentGenere depotDocumentGenere;
    private final ServiceStockage stockage;

    public ServiceArchiveDossier(DepotDossier depotDossier,
                                 DepotDocumentGenere depotDocumentGenere,
                                 ServiceStockage stockage) {
        this.depotDossier = depotDossier;
        this.depotDocumentGenere = depotDocumentGenere;
        this.stockage = stockage;
    }

    /**
     * @param pieces            ce qui a ete produit, dans l'ordre du sommaire
     * @param champsACompleter  ce qui manque encore, tous modeles confondus
     */
    public byte[] archiver(Dossier dossier,
                           List<PieceProduite> pieces,
                           List<String> champsACompleter) {

        var sortie = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(sortie, StandardCharsets.UTF_8)) {

            int numero = 1;
            for (PieceProduite piece : pieces) {
                String nom = "%02d - %s%s".formatted(
                        numero++, nettoyer(piece.libelle()), piece.extension());
                ecrire(zip, nom, piece.contenu());
            }

            ecrire(zip, "00 - SOMMAIRE.txt",
                    sommaire(dossier, pieces).getBytes(StandardCharsets.UTF_8));

            if (!champsACompleter.isEmpty()) {
                ecrire(zip, "A COMPLETER AVANT DEPOT.txt",
                        aCompleter(champsACompleter).getBytes(StandardCharsets.UTF_8));
            }

            zip.finish();
            return sortie.toByteArray();

        } catch (IOException erreur) {
            throw new RenduImpossibleException("Assemblage de l'archive impossible.", erreur);
        }
    }

    /** Reconstitue l'archive a partir des documents deja enregistres. */
    @Transactional(readOnly = true)
    public byte[] archiverDepuisLeStockage(UUID dossierId) {
        Dossier dossier = depotDossier.findById(dossierId).orElseThrow(
                () -> new IllegalArgumentException("Dossier inconnu."));

        List<PieceProduite> pieces = new ArrayList<>();
        for (DocumentGenere document
                : depotDocumentGenere.findAllByDossierIdOrderByGenereLeDesc(dossierId)) {
            try {
                pieces.add(new PieceProduite(
                        libelle(document),
                        document.getFormat() == DocumentGenere.Format.PDF ? ".pdf" : ".docx",
                        stockage.lire(document.getChemin()),
                        Modele.Categorie.AUTRE));
            } catch (IOException erreur) {
                LOG.warn("Document {} illisible, ecarte de l'archive : {}",
                        document.getId(), erreur.getMessage());
            }
        }

        if (pieces.isEmpty()) {
            throw new RenduImpossibleException(
                    "Aucun document n'a encore ete genere pour ce dossier.", null);
        }
        return archiver(dossier, pieces, List.of());
    }

    public String nomDeFichier(Dossier dossier, String intituleMarche) {
        return "%s - v%d - %s.zip".formatted(
                nettoyer(intituleMarche == null ? "dossier" : intituleMarche),
                dossier.getVersion(),
                LocalDate.now(ABIDJAN));
    }

    // ------------------------------------------------------------------ interne

    private String sommaire(Dossier dossier, List<PieceProduite> pieces) {
        StringBuilder texte = new StringBuilder();
        texte.append("SOMMAIRE DU DOSSIER\n");
        texte.append("===================\n\n");
        texte.append("Version   : ").append(dossier.getVersion()).append('\n');
        texte.append("Statut    : ").append(dossier.getStatut()).append('\n');
        texte.append("Assemblé  : ").append(LocalDate.now(ABIDJAN)).append("\n\n");

        int numero = 1;
        for (PieceProduite piece : pieces) {
            texte.append("%2d. %s%n".formatted(numero++, piece.libelle()));
        }

        if (dossier.porteFiligrane()) {
            texte.append("\nCe dossier n'est pas validé : chaque page porte la mention ");
            texte.append("BROUILLON et il ne doit pas être déposé en l'état.\n");
        }
        return texte.toString();
    }

    private String aCompleter(List<String> champs) {
        StringBuilder texte = new StringBuilder();
        texte.append("À COMPLÉTER AVANT DÉPÔT\n");
        texte.append("=======================\n\n");
        texte.append("Les informations suivantes manquent encore. Elles apparaissent dans\n");
        texte.append("les documents sous la forme [à compléter : ...].\n\n");
        champs.stream().distinct().sorted()
                .forEach(champ -> texte.append("  - ").append(champ).append('\n'));
        texte.append("\nUn document portant ces mentions ne doit pas être signé.\n");
        return texte.toString();
    }

    private void ecrire(ZipOutputStream zip, String nom, byte[] contenu) throws IOException {
        zip.putNextEntry(new ZipEntry(nom));
        zip.write(contenu);
        zip.closeEntry();
    }

    private String libelle(DocumentGenere document) {
        return switch (document.getType()) {
            case TECHNIQUE -> "Offre technique";
            case FINANCIERE -> "Offre financière";
            case RAPPORT_CONFORMITE -> "Rapport de conformité";
            case RECU -> "Reçu";
        };
    }

    /** Les noms de fichiers d'une archive traversent trop de systemes pour s'autoriser des fantaisies. */
    private String nettoyer(String libelle) {
        return libelle
                .replaceAll("[\\\\/:*?\"<>|]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Une piece prete a entrer dans l'archive.
     *
     * @param extension {@code .pdf} ou {@code .docx} : le format d'origine est
     *                  conserve, un Word reste modifiable
     */
    public record PieceProduite(String libelle,
                                String extension,
                                byte[] contenu,
                                Modele.Categorie categorie) {
    }
}
