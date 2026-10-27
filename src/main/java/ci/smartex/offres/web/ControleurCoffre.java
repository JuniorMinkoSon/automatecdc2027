package ci.smartex.offres.web;

import ci.smartex.offres.domain.PieceAdmin;
import ci.smartex.offres.jobs.TacheLecturePiece;
import ci.smartex.offres.repo.DepotPieceAdmin;
import ci.smartex.offres.securite.ContexteOrganisation;
import ci.smartex.offres.service.ServiceJournal;
import ci.smartex.offres.service.ServiceStockage;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Le coffre de pieces.
 *
 * <p>Une piece deposee n'est pas utilisable tout de suite : l'OCR propose son
 * type et ses dates, et c'est l'utilisateur qui confirme. Tant qu'il ne l'a pas
 * fait, aucun dossier ne la reprend. La machine lit, l'humain repond.
 */
@Controller
@RequestMapping("/coffre")
public class ControleurCoffre {

    private static final Set<String> EXTENSIONS_ADMISES =
            Set.of("pdf", "jpg", "jpeg", "png", "tif", "tiff");
    private static final long TAILLE_MAX = 32L * 1024 * 1024;

    private final DepotPieceAdmin depot;
    private final ServiceStockage stockage;
    private final ServiceJournal journal;
    private final JobScheduler ordonnanceur;

    public ControleurCoffre(DepotPieceAdmin depot,
                            ServiceStockage stockage,
                            ServiceJournal journal,
                            JobScheduler ordonnanceur) {
        this.depot = depot;
        this.stockage = stockage;
        this.journal = journal;
        this.ordonnanceur = ordonnanceur;
    }

    @GetMapping
    public String afficher(Model modele) {
        modele.addAttribute("rubrique", "coffre");
        modele.addAttribute("titre", "Coffre de pièces");
        modele.addAttribute("sousTitre",
                "Déposez une photo ou un PDF. La lecture propose les dates, "
                        + "vous confirmez.");
        modele.addAttribute("pieces", depot.findAllByOrganisationId(
                ContexteOrganisation.exigerOrganisation()));
        modele.addAttribute("types", PieceAdmin.Type.values());
        modele.addAttribute("aujourdhui", LocalDate.now());
        return "coffre/liste";
    }

    @PostMapping("/deposer")
    @Transactional
    public String deposer(@RequestParam PieceAdmin.Type type,
                          @RequestParam("fichier") MultipartFile fichier,
                          RedirectAttributes redirection) {

        String refus = verifier(fichier);
        if (refus != null) {
            redirection.addFlashAttribute("erreur", refus);
            return "redirect:/coffre";
        }

        UUID organisation = ContexteOrganisation.exigerOrganisation();
        PieceAdmin piece = new PieceAdmin();
        piece.setOrganisationId(organisation);
        piece.setType(type);

        try {
            piece.setFichierChemin(stockage.deposer("coffre",
                    fichier.getOriginalFilename(), fichier.getBytes()));
        } catch (IOException erreur) {
            redirection.addFlashAttribute("erreur",
                    "Le fichier n'a pas pu etre enregistre : " + erreur.getMessage());
            return "redirect:/coffre";
        }

        PieceAdmin enregistree = depot.save(piece);
        final UUID pieceId = enregistree.getId();
        final String nom = fichier.getOriginalFilename();
        journal.enregistrer("PIECE_DEPOSEE", "piece_admin", pieceId, nom);

        ordonnanceur.<TacheLecturePiece>enqueue(
                tache -> tache.executer(organisation, pieceId, nom));

        redirection.addFlashAttribute("message",
                "Piece deposee. Sa lecture automatique est en cours.");
        return "redirect:/coffre";
    }

    /**
     * La confirmation vaut engagement : a partir d'ici la piece entre dans les
     * dossiers, et sa date d'expiration fait foi pour les controles bloquants.
     */
    @PostMapping("/{id}/confirmer")
    @Transactional
    public String confirmer(@PathVariable UUID id,
                            @RequestParam(required = false) String numero,
                            @RequestParam(required = false) String dateEmission,
                            @RequestParam(required = false) String dateExpiration,
                            @RequestParam(required = false) Long montant,
                            RedirectAttributes redirection) {

        PieceAdmin piece = depot.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Piece inconnue."));

        piece.setNumero(vide(numero));
        piece.setDateEmission(date(dateEmission));
        piece.setDateExpiration(date(dateExpiration));
        if (piece.getType() == PieceAdmin.Type.CAUTIONNEMENT) {
            piece.setMontant(montant);
        }
        piece.setConfirmee(true);
        depot.save(piece);

        journal.enregistrer("PIECE_CONFIRMEE", "piece_admin", id);
        redirection.addFlashAttribute("message", "Piece confirmee.");
        return "redirect:/coffre";
    }

    private String verifier(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            return "Aucun fichier recu.";
        }
        if (fichier.getSize() > TAILLE_MAX) {
            return "Le fichier depasse 32 Mo.";
        }
        String nom = fichier.getOriginalFilename();
        if (nom == null || !nom.contains(".")) {
            return "Le fichier n'a pas d'extension reconnaissable.";
        }
        String extension = nom.substring(nom.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS_ADMISES.contains(extension)) {
            return "Format non accepte. Deposez un PDF ou une photo de la piece.";
        }
        return null;
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static LocalDate date(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valeur);
        } catch (Exception erreur) {
            return null;
        }
    }
}
