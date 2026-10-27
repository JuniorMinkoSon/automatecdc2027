package ci.smartex.offres.jobs;

import ci.smartex.offres.config.ProprietesApp;
import ci.smartex.offres.domain.PieceAdmin;
import ci.smartex.offres.extraction.*;
import ci.smartex.offres.repo.DepotPieceAdmin;
import ci.smartex.offres.securite.ContexteOrganisation;
import ci.smartex.offres.service.ServiceJournal;
import ci.smartex.offres.service.ServiceStockage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jobrunr.jobs.annotations.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * WF0 — Lecture d'une piece du coffre.
 *
 * <p>La piece est deja en base et deja chiffree quand cette tache demarre :
 * ce qu'elle ajoute, c'est la lecture des dates. La piece reste non confirmee
 * jusqu'a ce qu'un humain la valide d'un clic, et une piece non confirmee
 * n'entre dans aucun dossier.
 *
 * <p>Echec de lecture n'est pas echec de depot : la piece reste utilisable,
 * avec ses dates a saisir a la main.
 */
@Component
public class TacheLecturePiece {

    private static final Logger LOG = LoggerFactory.getLogger(TacheLecturePiece.class);

    private final ServiceStockage stockage;
    private final ClientGotenberg gotenberg;
    private final ClientOcr ocr;
    private final ExtracteurPiece extracteur;
    private final DepotPieceAdmin depot;
    private final ServiceJournal journal;
    private final ProprietesApp proprietes;
    private final ObjectMapper json = new ObjectMapper();

    public TacheLecturePiece(ServiceStockage stockage,
                             ClientGotenberg gotenberg,
                             ClientOcr ocr,
                             ExtracteurPiece extracteur,
                             DepotPieceAdmin depot,
                             ServiceJournal journal,
                             ProprietesApp proprietes) {
        this.stockage = stockage;
        this.gotenberg = gotenberg;
        this.ocr = ocr;
        this.extracteur = extracteur;
        this.depot = depot;
        this.journal = journal;
        this.proprietes = proprietes;
    }

    @Job(name = "Lecture de la piece %1", retries = 2)
    public void executer(UUID organisationId, UUID pieceId, String nomFichier) {
        ContexteOrganisation.dans(organisationId, () -> lire(pieceId, nomFichier));
    }

    @Transactional
    protected void lire(UUID pieceId, String nomFichier) {
        PieceAdmin piece = depot.findById(pieceId).orElseThrow(
                () -> new IllegalArgumentException("Piece inconnue : " + pieceId));

        if (!proprietes.getIa().isActive()) {
            return;
        }

        try {
            byte[] source = stockage.lire(piece.getFichierChemin());
            byte[] pdf = estImage(nomFichier) || estDejaPdf(nomFichier)
                    ? source
                    : gotenberg.versPdf(nomFichier, source);

            String texte = ocr.texte(pdf);
            ExtractionPiece lecture = extracteur.extraire(texte);

            // Le type lu ne remplace pas le type choisi par l'utilisateur : il
            // sait ce qu'il depose mieux qu'une lecture de tampon.
            if (piece.getLibelle() == null && !lecture.libelle.isBlank()) {
                piece.setLibelle(lecture.libelle);
            }
            if (piece.getNumero() == null) {
                piece.setNumero(vide(lecture.numero));
            }
            piece.setDateEmission(date(lecture.dateEmission));
            piece.setDateExpiration(date(lecture.dateExpiration));
            if (piece.getType() == PieceAdmin.Type.CAUTIONNEMENT) {
                piece.setMontant(montant(lecture.montant));
            }

            try {
                piece.setExtractionJson(json.writeValueAsString(lecture));
            } catch (Exception erreur) {
                LOG.warn("Lecture non serialisable pour la piece {}.", pieceId);
            }

            depot.save(piece);
            journal.enregistrer("PIECE_LUE", "piece_admin", pieceId,
                    lecture.reserve.isBlank() ? null : "reserve : " + lecture.reserve);

        } catch (Exception erreur) {
            // La piece reste deposee et utilisable : seules ses dates manquent,
            // et l'ecran du coffre les demandera.
            LOG.warn("Lecture de la piece {} impossible : {}", pieceId, erreur.getMessage());
            journal.enregistrer("PIECE_LECTURE_ECHEC", "piece_admin", pieceId,
                    erreur.getMessage());
        }
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static LocalDate date(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valeur.trim());
        } catch (Exception erreur) {
            return null;
        }
    }

    private static Long montant(String valeur) {
        String propre = valeur == null ? "" : valeur.replaceAll("[^0-9]", "");
        if (propre.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(propre);
        } catch (NumberFormatException erreur) {
            return null;
        }
    }

    private static boolean estDejaPdf(String nom) {
        return nom != null && nom.toLowerCase().endsWith(".pdf");
    }

    private static boolean estImage(String nom) {
        if (nom == null) {
            return false;
        }
        String minuscule = nom.toLowerCase();
        return minuscule.endsWith(".jpg") || minuscule.endsWith(".jpeg")
                || minuscule.endsWith(".png") || minuscule.endsWith(".tif")
                || minuscule.endsWith(".tiff");
    }
}
