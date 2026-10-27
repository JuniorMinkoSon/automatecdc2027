package ci.smartex.offres.web;

import ci.smartex.offres.domain.Modele;
import ci.smartex.offres.rendu.MoteurDocx;
import ci.smartex.offres.repo.DepotModele;
import ci.smartex.offres.securite.ContexteOrganisation;
import ci.smartex.offres.service.ServiceJournal;
import ci.smartex.offres.service.ServiceStockage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Les modeles de documents de l'entreprise.
 *
 * <p>C'est ici qu'un soumissionnaire depose les documents Word qu'il a mis au
 * point au fil des marches. Ils sont lus pour en extraire les champs, puis
 * remplis en conservant leur mise en page. Les reconstruire les priverait de
 * leur forme, et un dossier qui ne ressemble plus a celui que la commission a
 * l'habitude de lire se remarque.
 *
 * <p>Les champs se notent entre doubles accolades dans le document, par
 * exemple {@code {{ENTREPRISE_RAISON_SOCIALE}}}. L'application les detecte
 * seule : aucune liste de questions n'est codee en dur.
 */
@Controller
@RequestMapping("/modeles")
public class ControleurModeles {

    private static final long TAILLE_MAX = 16L * 1024 * 1024;

    private final DepotModele depot;
    private final MoteurDocx moteur;
    private final ServiceStockage stockage;
    private final ServiceJournal journal;
    private final ObjectMapper json = new ObjectMapper();

    public ControleurModeles(DepotModele depot,
                             MoteurDocx moteur,
                             ServiceStockage stockage,
                             ServiceJournal journal) {
        this.depot = depot;
        this.moteur = moteur;
        this.stockage = stockage;
        this.journal = journal;
    }

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("rubrique", "modeles");
        modele.addAttribute("modeles", depot.findAllByActifTrueOrderByOrdreSommaireAsc());
        modele.addAttribute("categories", Modele.Categorie.values());
        modele.addAttribute("offres", Modele.Offre.values());
        return "modeles/liste";
    }

    @PostMapping("/deposer")
    @Transactional
    public String deposer(@RequestParam String code,
                          @RequestParam String libelle,
                          @RequestParam Modele.Categorie categorie,
                          @RequestParam Modele.Offre offre,
                          @RequestParam(defaultValue = "50") int ordreSommaire,
                          @RequestParam("fichier") MultipartFile fichier,
                          RedirectAttributes redirection) {

        String refus = verifier(fichier);
        if (refus != null) {
            redirection.addFlashAttribute("erreur", refus);
            return "redirect:/modeles";
        }

        byte[] contenu;
        Set<String> champs;
        try {
            contenu = fichier.getBytes();
            // Lire les champs tout de suite valide le fichier : un document
            // illisible est refuse maintenant, et non au moment ou quelqu'un
            // genere un dossier sous echeance.
            champs = moteur.champsAttendus(contenu);
        } catch (IOException erreur) {
            redirection.addFlashAttribute("erreur", "Lecture du fichier impossible.");
            return "redirect:/modeles";
        } catch (RuntimeException erreur) {
            redirection.addFlashAttribute("erreur", erreur.getMessage());
            return "redirect:/modeles";
        }

        UUID organisation = ContexteOrganisation.exigerOrganisation();
        Modele modele = new Modele();
        modele.setOrganisationId(organisation);
        modele.setCode(code.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_"));
        modele.setLibelle(libelle.trim());
        modele.setCategorie(categorie);
        modele.setOffre(offre);
        modele.setFormat(Modele.Format.DOCX);
        modele.setOrdreSommaire(ordreSommaire);
        modele.setVersion(prochaineVersion(modele.getCode(), organisation));

        try {
            modele.setFichierChemin(stockage.deposer("modeles",
                    fichier.getOriginalFilename(), contenu));
            modele.setChampsDetectes(json.writeValueAsString(champs));
        } catch (Exception erreur) {
            redirection.addFlashAttribute("erreur",
                    "Enregistrement impossible : " + erreur.getMessage());
            return "redirect:/modeles";
        }

        Modele enregistre = depot.save(modele);
        journal.enregistrer("MODELE_DEPOSE", "modele", enregistre.getId(),
                champs.size() + " champ(s) détecté(s)");

        redirection.addFlashAttribute("message", champs.isEmpty()
                ? "Modèle enregistré. Aucun champ {{...}} n'y a été trouvé : "
                  + "il sera joint tel quel."
                : "Modèle enregistré. Champs détectés : " + String.join(", ", champs) + ".");
        return "redirect:/modeles";
    }

    @PostMapping("/{id}/retirer")
    @Transactional
    public String retirer(@PathVariable UUID id, RedirectAttributes redirection) {
        var trouve = depot.findById(id);
        if (trouve.isEmpty()) {
            redirection.addFlashAttribute("erreur", "Modèle inconnu.");
            return "redirect:/modeles";
        }

        Modele modele = trouve.get();
        if (!modele.estTeleverse()) {
            // Les modeles communs sont livres avec l'application : les retirer
            // priverait toutes les entreprises d'un formulaire officiel.
            redirection.addFlashAttribute("erreur",
                    "Ce modèle est commun à toutes les entreprises et ne peut pas "
                            + "être retiré.");
            return "redirect:/modeles";
        }

        modele.setActif(false);
        depot.save(modele);
        journal.enregistrer("MODELE_RETIRE", "modele", id);
        redirection.addFlashAttribute("message", "Modèle retiré du sommaire.");
        return "redirect:/modeles";
    }

    private int prochaineVersion(String code, UUID organisation) {
        return depot.findFirstByCodeAndActifTrueOrderByVersionDesc(code)
                .filter(existant -> organisation.equals(existant.getOrganisationId()))
                .map(existant -> existant.getVersion() + 1)
                .orElse(1);
    }

    private String verifier(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            return "Aucun fichier reçu.";
        }
        if (fichier.getSize() > TAILLE_MAX) {
            return "Le modèle dépasse 16 Mo.";
        }
        String nom = fichier.getOriginalFilename();
        if (nom == null || !nom.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            return "Seuls les fichiers .docx sont acceptés. Un .doc doit être "
                    + "réenregistré au format .docx depuis Word.";
        }
        return null;
    }
}
