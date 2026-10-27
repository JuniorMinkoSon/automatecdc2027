package ci.smartex.offres.web;

import ci.smartex.offres.domain.AppelOffres;
import ci.smartex.offres.domain.Lot;
import ci.smartex.offres.rendu.ServiceAvisAppelOffres;
import ci.smartex.offres.rendu.ServiceDossierAppelOffres;
import ci.smartex.offres.repo.DepotAppelOffres;
import ci.smartex.offres.repo.DepotLot;
import ci.smartex.offres.repo.DepotSectionDao;
import ci.smartex.offres.securite.ContexteOrganisation;
import ci.smartex.offres.service.ServiceJournal;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Le cote autorite contractante : preparer et publier un dossier d'appel
 * d'offres.
 *
 * <p>Symetrique du cote soumissionnaire, et pour la meme raison : des dizaines
 * de pages dont une vingtaine de champs seulement changent. Ici l'objet du
 * marche est saisi une fois et repris par l'avis, la page de garde, le sommaire
 * et chaque section.
 */
@Controller
@RequestMapping("/publications")
public class ControleurPublication {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final DepotAppelOffres depotAppelOffres;
    private final DepotLot depotLot;
    private final DepotSectionDao depotSection;
    private final ServiceAvisAppelOffres serviceAvis;
    private final ServiceDossierAppelOffres serviceDossierAo;
    private final ServiceJournal journal;

    public ControleurPublication(DepotAppelOffres depotAppelOffres,
                                 DepotLot depotLot,
                                 DepotSectionDao depotSection,
                                 ServiceAvisAppelOffres serviceAvis,
                                 ServiceDossierAppelOffres serviceDossierAo,
                                 ServiceJournal journal) {
        this.depotAppelOffres = depotAppelOffres;
        this.depotLot = depotLot;
        this.depotSection = depotSection;
        this.serviceAvis = serviceAvis;
        this.serviceDossierAo = serviceDossierAo;
        this.journal = journal;
    }

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("rubrique", "publications");
        modele.addAttribute("titre", "Mes appels d'offres");
        modele.addAttribute("sousTitre",
                "Les marchés que vous publiez, et leur dossier.");
        modele.addAttribute("appels", depotAppelOffres
                .findAllByOrganisationIdOrderByCreeLeDesc(ContexteOrganisation.exigerOrganisation())
                .stream()
                .filter(AppelOffres::estPublie)
                .toList());
        return "publication/liste";
    }

    @PostMapping("/nouveau")
    @Transactional
    public String creer(@RequestParam String intitule, RedirectAttributes redirection) {
        if (intitule == null || intitule.isBlank()) {
            redirection.addFlashAttribute("erreur",
                    "L'objet du marché est obligatoire : il apparaît sur toutes les sections.");
            return "redirect:/publications";
        }

        AppelOffres appel = new AppelOffres();
        appel.setOrganisationId(ContexteOrganisation.exigerOrganisation());
        appel.setRole(AppelOffres.Role.PUBLICATION);
        appel.setIntitule(intitule.trim());
        appel.setStatut(AppelOffres.Statut.A_VERIFIER);
        appel.setModePassation("Appel d'offres ouvert");

        UUID identifiant = depotAppelOffres.save(appel).getId();
        serviceDossierAo.preparerLesSections(identifiant);

        journal.enregistrer("DAO_CREE", "appel_offres", identifiant, intitule);
        redirection.addFlashAttribute("message",
                "Dossier créé. Les sections réglementaires sont déjà posées.");
        return "redirect:/publications/" + identifiant;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model modele) {
        AppelOffres appel = charger(id);

        modele.addAttribute("rubrique", "publications");
        modele.addAttribute("titre", appel.getIntitule());
        modele.addAttribute("sousTitre",
                "Remplissez une fois : l'avis, la page de garde et les sections le reprennent.");
        modele.addAttribute("appel", appel);
        modele.addAttribute("lots", depotLot.findAllByAppelOffresIdOrderByNumeroAsc(id));
        modele.addAttribute("sections", serviceDossierAo.preparerLesSections(id));
        modele.addAttribute("rapport", serviceAvis.controler(id));
        return "publication/detail";
    }

    /**
     * Une seule saisie alimente l'avis, la page de garde et toutes les
     * sections. C'est l'inverse du dossier redige a la main, ou chacun de ces
     * champs est recopie et finit par diverger.
     */
    @PostMapping("/{id}")
    @Transactional
    public String enregistrer(@PathVariable UUID id,
                              @RequestParam(required = false) String numeroAo,
                              @RequestParam(required = false) String autoriteContractante,
                              @RequestParam(required = false) String financement,
                              @RequestParam(required = false) String ligneBudgetaire,
                              @RequestParam(required = false) String modePassation,
                              @RequestParam(required = false) String adresseRetrait,
                              @RequestParam(required = false) String contacts,
                              @RequestParam(required = false) String qualificationsExigees,
                              @RequestParam(required = false) Long prixDossier,
                              @RequestParam(required = false) String moyenPaiementDossier,
                              @RequestParam(required = false) String plateformeDepot,
                              @RequestParam(required = false) String urlDepot,
                              @RequestParam(required = false) String dateLimiteDepot,
                              @RequestParam(required = false) String ouverturePlis,
                              @RequestParam(required = false) String lieuOuverture,
                              @RequestParam(required = false) Integer validiteAnnonceeJours,
                              @RequestParam(required = false) Integer validiteOffreJours,
                              RedirectAttributes redirection) {

        AppelOffres appel = charger(id);
        appel.setNumeroAo(vide(numeroAo));
        appel.setAutoriteContractante(vide(autoriteContractante));
        appel.setFinancement(vide(financement));
        appel.setLigneBudgetaire(vide(ligneBudgetaire));
        appel.setModePassation(vide(modePassation));
        appel.setAdresseRetrait(vide(adresseRetrait));
        appel.setContacts(vide(contacts));
        appel.setQualificationsExigees(vide(qualificationsExigees));
        appel.setPrixDossier(prixDossier);
        appel.setMoyenPaiementDossier(vide(moyenPaiementDossier));
        appel.setPlateformeDepot(vide(plateformeDepot));
        appel.setUrlDepot(vide(urlDepot));
        appel.setDateLimiteDepot(horodatage(dateLimiteDepot));
        appel.setOuverturePlis(horodatage(ouverturePlis));
        appel.setLieuOuverture(vide(lieuOuverture));
        appel.setValiditeAnnonceeJours(validiteAnnonceeJours);
        appel.setValiditeOffreJours(validiteOffreJours);
        depotAppelOffres.save(appel);

        journal.enregistrer("DAO_MODIFIE", "appel_offres", id);
        redirection.addFlashAttribute("message", "Enregistré.");
        return "redirect:/publications/" + id;
    }

    @PostMapping("/{id}/lots")
    @Transactional
    public String ajouterLot(@PathVariable UUID id,
                             @RequestParam String intitule,
                             @RequestParam(required = false) String localite,
                             @RequestParam(required = false) Integer delaiMois,
                             @RequestParam(required = false) Long montantGarantie,
                             RedirectAttributes redirection) {

        charger(id);
        int suivant = depotLot.findAllByAppelOffresIdOrderByNumeroAsc(id).stream()
                .mapToInt(Lot::getNumero).max().orElse(0) + 1;

        Lot lot = new Lot();
        lot.setOrganisationId(ContexteOrganisation.exigerOrganisation());
        lot.setAppelOffresId(id);
        lot.setNumero(suivant);
        lot.setIntitule(intitule.trim());
        lot.setLocalite(vide(localite));
        lot.setDelaiMois(delaiMois);
        lot.setMontantGarantie(montantGarantie);
        depotLot.save(lot);

        redirection.addFlashAttribute("message", "Lot " + suivant + " ajouté.");
        return "redirect:/publications/" + id;
    }

    @PostMapping("/{id}/sections/{code}")
    @Transactional
    public String enregistrerSection(@PathVariable UUID id,
                                     @PathVariable String code,
                                     @RequestParam String contenu,
                                     RedirectAttributes redirection) {

        depotSection.findByAppelOffresIdAndCode(id, code).ifPresentOrElse(section -> {
            if (!section.isModifiable()) {
                // Les sections figees par le code des marches ne se modifient
                // pas : une clause glissee la rendrait le dossier attaquable.
                redirection.addFlashAttribute("erreur",
                        "La section « " + section.getIntitule() + " » est réglementaire "
                                + "et ne peut pas être modifiée.");
                return;
            }
            section.setContenu(contenu);
            section.setMajLe(Instant.now());
            depotSection.save(section);
            redirection.addFlashAttribute("message",
                    "Section « " + section.getIntitule() + " » enregistrée.");
        }, () -> redirection.addFlashAttribute("erreur", "Section inconnue."));

        return "redirect:/publications/" + id;
    }

    // ------------------------------------------------------------------ PDF

    @GetMapping("/{id}/avis.pdf")
    public ResponseEntity<ByteArrayResource> avis(@PathVariable UUID id) {
        AppelOffres appel = charger(id);
        return pdf(serviceAvis.produire(id),
                "Avis d'appel d'offres - " + numero(appel) + ".pdf");
    }

    @GetMapping("/{id}/dossier.pdf")
    public ResponseEntity<ByteArrayResource> dossier(@PathVariable UUID id) {
        AppelOffres appel = charger(id);
        return pdf(serviceDossierAo.produire(id),
                "Dossier d'appel d'offres - " + numero(appel) + ".pdf");
    }

    // ------------------------------------------------------------------ outils

    private AppelOffres charger(UUID id) {
        AppelOffres appel = depotAppelOffres.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Appel d'offres inconnu."));
        if (!appel.estPublie()) {
            throw new IllegalArgumentException(
                    "Cet appel d'offres est une réponse, pas une publication.");
        }
        return appel;
    }

    private String numero(AppelOffres appel) {
        return appel.getNumeroAo() == null || appel.getNumeroAo().isBlank()
                ? "sans numero" : appel.getNumeroAo();
    }

    private ResponseEntity<ByteArrayResource> pdf(byte[] contenu, String nom) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(nom))
                .body(new ByteArrayResource(contenu));
    }

    private String disposition(String nom) {
        String simple = java.text.Normalizer.normalize(nom, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('\'', ' ');
        String encode = java.net.URLEncoder.encode(nom, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "attachment; filename=\"" + simple + "\"; filename*=UTF-8''" + encode;
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static Instant horodatage(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(valeur).atZone(ABIDJAN).toInstant();
        } catch (Exception erreur) {
            try {
                return java.time.LocalDate.parse(valeur).atTime(9, 0)
                        .atZone(ABIDJAN).toInstant();
            } catch (Exception ignore) {
                return null;
            }
        }
    }
}
