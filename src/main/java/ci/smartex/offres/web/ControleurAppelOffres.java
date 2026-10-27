package ci.smartex.offres.web;

import ci.smartex.offres.domain.AppelOffres;
import ci.smartex.offres.jobs.TacheAnalyseDao;
import ci.smartex.offres.repo.DepotAppelOffres;
import ci.smartex.offres.repo.DepotExigence;
import ci.smartex.offres.repo.DepotLot;
import ci.smartex.offres.securite.ContexteOrganisation;
import ci.smartex.offres.service.ServiceDossier;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Les appels d'offres : televersement du DAO, analyse, relecture.
 *
 * <p>L'analyse part en tache de fond des le televersement. L'utilisateur n'a
 * pas a attendre devant son ecran qu'un document de cent pages soit lu, et
 * surtout : si l'analyse echoue, le DAO reste la et les champs se saisissent a
 * la main. L'extraction accelere le travail, elle n'en est pas la condition.
 */
@Controller
@RequestMapping("/appels-offres")
public class ControleurAppelOffres {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    /** Ce que le coffre et les DAO acceptent, et rien d'autre. */
    private static final Set<String> EXTENSIONS_ADMISES =
            Set.of("pdf", "doc", "docx", "odt", "rtf");

    private static final long TAILLE_MAX = 64L * 1024 * 1024;

    private final DepotAppelOffres depotAppelOffres;
    private final DepotLot depotLot;
    private final DepotExigence depotExigence;
    private final ServiceStockage stockage;
    private final ServiceDossier serviceDossier;
    private final ServiceJournal journal;
    private final JobScheduler ordonnanceur;

    public ControleurAppelOffres(DepotAppelOffres depotAppelOffres,
                                 DepotLot depotLot,
                                 DepotExigence depotExigence,
                                 ServiceStockage stockage,
                                 ServiceDossier serviceDossier,
                                 ServiceJournal journal,
                                 JobScheduler ordonnanceur) {
        this.depotAppelOffres = depotAppelOffres;
        this.depotLot = depotLot;
        this.depotExigence = depotExigence;
        this.stockage = stockage;
        this.serviceDossier = serviceDossier;
        this.journal = journal;
        this.ordonnanceur = ordonnanceur;
    }

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("rubrique", "appels");
        modele.addAttribute("titre", "Appels d'offres");
        modele.addAttribute("sousTitre", "Les marchés auxquels vous répondez.");
        modele.addAttribute("appels", depotAppelOffres.findAllByOrganisationIdOrderByCreeLeDesc(
                ContexteOrganisation.exigerOrganisation()));
        return "ao/liste";
    }

    @GetMapping("/nouveau")
    public String formulaire(Model modele) {
        modele.addAttribute("rubrique", "appels");
        modele.addAttribute("titre", "Nouvel appel d'offres");
        modele.addAttribute("sousTitre",
                "Déposez le DAO. Son analyse démarre seule et vous rendez la main.");
        return "ao/nouveau";
    }

    @PostMapping("/nouveau")
    @Transactional
    public String televerser(@RequestParam("intitule") String intitule,
                             @RequestParam("dao") MultipartFile fichier,
                             RedirectAttributes redirection) {

        if (intitule == null || intitule.isBlank()) {
            redirection.addFlashAttribute("erreur",
                    "L'intitulé du marché est obligatoire : il apparaît sur toutes les pièces.");
            return "redirect:/appels-offres/nouveau";
        }

        String refus = verifierFichier(fichier);
        if (refus != null) {
            redirection.addFlashAttribute("erreur", refus);
            return "redirect:/appels-offres/nouveau";
        }

        UUID organisation = ContexteOrganisation.exigerOrganisation();
        AppelOffres appel = new AppelOffres();
        appel.setOrganisationId(organisation);
        appel.setIntitule(intitule.trim());
        appel.setStatut(AppelOffres.Statut.EN_ANALYSE);

        try {
            appel.setDaoChemin(stockage.deposer("dao",
                    fichier.getOriginalFilename(), fichier.getBytes()));
        } catch (IOException erreur) {
            redirection.addFlashAttribute("erreur",
                    "Le fichier n'a pas pu être enregistré : " + erreur.getMessage());
            return "redirect:/appels-offres/nouveau";
        }

        AppelOffres enregistre = depotAppelOffres.save(appel);
        final UUID appelId = enregistre.getId();
        final String nomFichier = fichier.getOriginalFilename();
        journal.enregistrer("DAO_TELEVERSE", "appel_offres", appelId, nomFichier);

        // L'organisation voyage avec la tache : une tache de fond n'a pas de
        // session, donc pas de contexte, et sans lui la base ne lui montrerait
        // rien du tout. JobRunr resout la tache depuis le contexte Spring au
        // moment de l'execution, ce qui lui rend ses dependances.
        ordonnanceur.<TacheAnalyseDao>enqueue(
                tache -> tache.executer(organisation, appelId, nomFichier));

        redirection.addFlashAttribute("message",
                "Appel d'offres enregistré. L'analyse du DAO est en cours.");
        return "redirect:/appels-offres/" + enregistre.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model modele) {
        AppelOffres appel = depotAppelOffres.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Appel d'offres inconnu."));

        modele.addAttribute("rubrique", "appels");
        modele.addAttribute("titre", appel.getIntitule());
        modele.addAttribute("sousTitre",
                "Relisez les champs extraits à côté de leur source avant de produire "
                        + "des pièces.");
        modele.addAttribute("appel", appel);
        modele.addAttribute("lots", depotLot.findAllByAppelOffresIdOrderByNumeroAsc(id));
        modele.addAttribute("exigences", depotExigence.findAllByAppelOffresId(id));
        modele.addAttribute("champsManquants", champsManquants(appel));
        return "ao/detail";
    }

    /**
     * Enregistre la relecture humaine. C'est ce passage, et lui seul, qui fait
     * passer l'appel d'offres de « a verifier » a « verifie » : aucune piece
     * ne sort de donnees que personne n'a regardees.
     */
    @PostMapping("/{id}/verifier")
    @Transactional
    public String verifier(@PathVariable UUID id,
                           @RequestParam(required = false) String numeroAo,
                           @RequestParam(required = false) String autoriteContractante,
                           @RequestParam(required = false) String destinataire,
                           @RequestParam(required = false) Integer validiteOffreJours,
                           @RequestParam(required = false) String dateLimiteDepot,
                           @RequestParam(required = false) String tvaApplicable,
                           RedirectAttributes redirection) {

        AppelOffres appel = depotAppelOffres.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Appel d'offres inconnu."));

        appel.setNumeroAo(vide(numeroAo));
        appel.setAutoriteContractante(vide(autoriteContractante));
        appel.setDestinataire(vide(destinataire));
        appel.setValiditeOffreJours(validiteOffreJours);
        appel.setDateLimiteDepot(horodatage(dateLimiteDepot));
        appel.setTvaApplicable(tva(tvaApplicable));

        List<String> manquants = champsManquants(appel);
        if (manquants.isEmpty()) {
            appel.setStatut(AppelOffres.Statut.VERIFIE);
        }
        depotAppelOffres.save(appel);
        journal.enregistrer("DAO_VERIFIE", "appel_offres", id);

        redirection.addFlashAttribute("message", manquants.isEmpty()
                ? "Appel d'offres vérifié. Vous pouvez créer les dossiers."
                : "Enregistré. Il reste à compléter : " + String.join(", ", manquants) + ".");
        return "redirect:/appels-offres/" + id;
    }

    @PostMapping("/{id}/dossier")
    @Transactional
    public String creerDossier(@PathVariable UUID id,
                               @RequestParam(required = false) UUID lotId,
                               RedirectAttributes redirection) {
        var dossier = serviceDossier.creer(id, lotId);
        redirection.addFlashAttribute("message", "Dossier créé.");
        return "redirect:/dossiers/" + dossier.getId();
    }

    @PostMapping("/{id}/relancer-analyse")
    public String relancer(@PathVariable UUID id, RedirectAttributes redirection) {
        UUID organisation = ContexteOrganisation.exigerOrganisation();
        AppelOffres appel = depotAppelOffres.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Appel d'offres inconnu."));

        final String nomFichier = appel.getDaoChemin();
        ordonnanceur.<TacheAnalyseDao>enqueue(
                tache -> tache.executer(organisation, id, nomFichier));

        redirection.addFlashAttribute("message", "Analyse relancée.");
        return "redirect:/appels-offres/" + id;
    }

    // ------------------------------------------------------------------ outils

    /**
     * Ce qui empeche encore de generer des pieces fiables. Ces champs partent
     * dans toutes les lettres : les laisser vides imprimerait des blancs.
     */
    private List<String> champsManquants(AppelOffres appel) {
        List<String> manquants = new java.util.ArrayList<>();
        if (estVide(appel.getNumeroAo())) {
            manquants.add("numéro d'appel d'offres");
        }
        if (estVide(appel.getAutoriteContractante())) {
            manquants.add("autorité contractante");
        }
        if (appel.getValiditeOffreJours() == null) {
            manquants.add("durée de validité de l'offre");
        }
        if (appel.getDateLimiteDepot() == null) {
            manquants.add("date limite de dépôt");
        }
        return manquants;
    }

    private String verifierFichier(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            return "Aucun fichier reçu.";
        }
        if (fichier.getSize() > TAILLE_MAX) {
            return "Le fichier dépasse 64 Mo.";
        }
        String nom = fichier.getOriginalFilename();
        if (nom == null || !nom.contains(".")) {
            return "Le fichier n'a pas d'extension reconnaissable.";
        }
        String extension = nom.substring(nom.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS_ADMISES.contains(extension)) {
            return "Format non accepté. Déposez un PDF ou un document Word.";
        }
        return null;
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static Boolean tva(String valeur) {
        if (valeur == null || valeur.isBlank() || "indetermine".equals(valeur)) {
            return null;
        }
        return "oui".equals(valeur);
    }

    private static Instant horodatage(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(valeur).atZone(ABIDJAN).toInstant();
        } catch (Exception erreur) {
            try {
                return java.time.LocalDate.parse(valeur).atTime(23, 59)
                        .atZone(ABIDJAN).toInstant();
            } catch (Exception ignore) {
                return null;
            }
        }
    }

    private static boolean estVide(String valeur) {
        return valeur == null || valeur.isBlank();
    }
}
