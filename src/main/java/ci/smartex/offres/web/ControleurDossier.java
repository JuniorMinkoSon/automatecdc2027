package ci.smartex.offres.web;

import ci.smartex.offres.conformite.MoteurConformite;
import ci.smartex.offres.domain.AppelOffres;
import ci.smartex.offres.domain.DocumentGenere;
import ci.smartex.offres.domain.Dossier;
import ci.smartex.offres.domain.LigneDqe;
import ci.smartex.offres.domain.TachePlanning;
import ci.smartex.offres.rendu.ServiceArchiveDossier;
import ci.smartex.offres.rendu.ServiceGenerationDossier;
import ci.smartex.offres.repo.*;
import ci.smartex.offres.securite.ContexteOrganisation;
import ci.smartex.offres.service.*;
import ci.smartex.offres.util.MontantEnLettres;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le parcours d'un dossier : engagements, prix, contrôles, génération, dépôt.
 *
 * <p>Le controleur ne decide de rien. Il ne verifie pas si la validation est
 * possible, il ne calcule aucun total : les services le font, et refusent
 * eux-memes. Une regle qui ne vivrait que dans un controleur serait
 * contournable par la prochaine page qu'on ajoute.
 */
@Controller
@RequestMapping("/dossiers")
public class ControleurDossier {

    private final ServiceDossier serviceDossier;
    private final ServiceDqe serviceDqe;
    private final ServiceRapprochement rapprochement;
    private final MoteurConformite moteur;
    private final ServiceGenerationDossier generation;
    private final ServiceArchiveDossier archive;
    private final DepotDossier depotDossier;
    private final DepotAppelOffres depotAppelOffres;
    private final DepotLot depotLot;
    private final DepotDocumentGenere depotDocumentGenere;
    private final DepotTachePlanning depotTachePlanning;

    public ControleurDossier(ServiceDossier serviceDossier,
                             ServiceDqe serviceDqe,
                             ServiceRapprochement rapprochement,
                             MoteurConformite moteur,
                             ServiceGenerationDossier generation,
                             ServiceArchiveDossier archive,
                             DepotDossier depotDossier,
                             DepotAppelOffres depotAppelOffres,
                             DepotLot depotLot,
                             DepotDocumentGenere depotDocumentGenere,
                             DepotTachePlanning depotTachePlanning) {
        this.serviceDossier = serviceDossier;
        this.serviceDqe = serviceDqe;
        this.rapprochement = rapprochement;
        this.moteur = moteur;
        this.generation = generation;
        this.archive = archive;
        this.depotDossier = depotDossier;
        this.depotAppelOffres = depotAppelOffres;
        this.depotLot = depotLot;
        this.depotDocumentGenere = depotDocumentGenere;
        this.depotTachePlanning = depotTachePlanning;
    }

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("rubrique", "dossiers");
        modele.addAttribute("titre", "Dossiers");
        modele.addAttribute("sousTitre", "Toutes les réponses, en cours et déposées.");
        modele.addAttribute("dossiers", depotDossier.findAllByOrganisationIdOrderByMajLeDesc(
                ContexteOrganisation.exigerOrganisation()));
        return "dossier/liste";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model modele) {
        poserDossier(id, modele);
        modele.addAttribute("rapport", moteur.dernierRapport(id));
        modele.addAttribute("documents",
                depotDocumentGenere.findAllByDossierIdOrderByGenereLeDesc(id));
        modele.addAttribute("taches", depotTachePlanning.findAllByDossierIdOrderByOrdreAsc(id));
        return "dossier/detail";
    }

    @PostMapping("/{id}/engagements")
    public String engagements(@PathVariable UUID id,
                              @RequestParam(required = false) Integer delaiExecutionMois,
                              @RequestParam(required = false) Integer validiteOffreJours,
                              @RequestParam(required = false) Integer validiteGarantieJours,
                              RedirectAttributes redirection) {
        try {
            serviceDossier.enregistrerEngagements(id, delaiExecutionMois,
                    validiteOffreJours, validiteGarantieJours, null);
            moteur.executer(id);
            redirection.addFlashAttribute("message", "Engagements enregistrés.");
        } catch (DossierFigeException erreur) {
            redirection.addFlashAttribute("erreur", erreur.getMessage());
        }
        return "redirect:/dossiers/" + id;
    }

    // ------------------------------------------------------------------ prix

    @GetMapping("/{id}/prix")
    public String prix(@PathVariable UUID id, Model modele) {
        poserDossier(id, modele);

        List<LigneDqe> lignes = serviceDqe.lignes(id);
        long total = serviceDqe.totalProvisoire(lignes);

        modele.addAttribute("titre", "Saisie des prix");
        modele.addAttribute("sousTitre",
                "Seuls les prix unitaires se saisissent. Montants, totaux et lettres "
                        + "sont calculés.");
        modele.addAttribute("lignes", lignes);
        modele.addAttribute("total", total);
        modele.addAttribute("totalChiffres", MontantEnLettres.chiffres(total));
        modele.addAttribute("totalLettres", MontantEnLettres.montant(total));
        modele.addAttribute("prixManquants", serviceDqe.nombreDePrixManquants(id));
        return "dossier/prix";
    }

    /**
     * N'accepte que les prix unitaires. Designations, unites et quantites
     * viennent du DAO et ne sont pas dans le formulaire : les rendre
     * modifiables ouvrirait la porte a un bordereau qui ne correspond plus a
     * celui de l'appel d'offres.
     */
    @PostMapping("/{id}/prix")
    public String enregistrerPrix(@PathVariable UUID id,
                                  @RequestParam Map<String, String> champs,
                                  RedirectAttributes redirection) {
        Map<UUID, Long> prix = new HashMap<>();
        for (Map.Entry<String, String> champ : champs.entrySet()) {
            if (!champ.getKey().startsWith("prix_")) {
                continue;
            }
            UUID ligne = UUID.fromString(champ.getKey().substring("prix_".length()));
            String valeur = champ.getValue() == null ? "" : champ.getValue().trim();
            prix.put(ligne, valeur.isEmpty() ? null : Long.parseLong(valeur));
        }

        try {
            long total = serviceDqe.enregistrerPrix(id, prix);
            moteur.executer(id);
            redirection.addFlashAttribute("message",
                    "Prix enregistrés. Total : " + MontantEnLettres.chiffres(total) + ".");
        } catch (DossierFigeException | IllegalArgumentException erreur) {
            redirection.addFlashAttribute("erreur", erreur.getMessage());
        }
        return "redirect:/dossiers/" + id + "/prix";
    }

    // ------------------------------------------------------------------ planning

    /**
     * Ajoute une tache au planning.
     *
     * <p>La commission verifie une seule chose : que la derniere tache
     * s'acheve dans le delai engage. Le formulaire signale le depassement des
     * la saisie, plutot que de le laisser decouvrir a l'execution.
     */
    @PostMapping("/{id}/planning")
    @Transactional
    public String ajouterTache(@PathVariable UUID id,
                               @RequestParam String libelle,
                               @RequestParam(defaultValue = "1") int debutMois,
                               @RequestParam(defaultValue = "1") int dureeMois,
                               RedirectAttributes redirection) {

        Dossier dossier = serviceDossier.charger(id);
        if (!dossier.estModifiable()) {
            redirection.addFlashAttribute("erreur",
                    "Ce dossier est figé : créez une nouvelle version pour le corriger.");
            return "redirect:/dossiers/" + id;
        }

        int suivant = depotTachePlanning.findAllByDossierIdOrderByOrdreAsc(id).stream()
                .mapToInt(TachePlanning::getOrdre).max().orElse(0) + 1;

        TachePlanning tache = new TachePlanning();
        tache.setOrganisationId(ContexteOrganisation.exigerOrganisation());
        tache.setDossierId(id);
        tache.setLibelle(libelle.trim());
        tache.setDebutMois(Math.max(1, debutMois));
        tache.setDureeMois(Math.max(1, dureeMois));
        tache.setOrdre(suivant);
        depotTachePlanning.save(tache);

        Integer delai = dossier.getDelaiExecutionMois();
        if (delai != null && tache.finMois() > delai) {
            redirection.addFlashAttribute("erreur",
                    "Cette tâche s'achève au mois %d, au-delà du délai engagé de %d mois."
                            .formatted(tache.finMois(), delai));
        } else {
            redirection.addFlashAttribute("message", "Tâche ajoutée.");
        }
        return "redirect:/dossiers/" + id;
    }

    @PostMapping("/{id}/planning/{tacheId}/retirer")
    @Transactional
    public String retirerTache(@PathVariable UUID id, @PathVariable UUID tacheId,
                               RedirectAttributes redirection) {
        depotTachePlanning.findById(tacheId)
                .filter(tache -> tache.getDossierId().equals(id))
                .ifPresent(depotTachePlanning::delete);
        redirection.addFlashAttribute("message", "Tâche retirée.");
        return "redirect:/dossiers/" + id;
    }

    /**
     * La declaration sur les marches non executes.
     *
     * <p>Elle ne se deduit pas : un marche inexecute ne laisse pas de trace
     * chez celui qui ne l'a pas execute. C'est donc le soumissionnaire qui
     * declare, et il engage sa responsabilite.
     */
    @PostMapping("/{id}/antecedents")
    @Transactional
    public String declarerAntecedents(@PathVariable UUID id,
                                      @RequestParam(defaultValue = "false") boolean sansAntecedent,
                                      @RequestParam(required = false) String detail,
                                      RedirectAttributes redirection) {

        Dossier dossier = serviceDossier.charger(id);
        if (!dossier.estModifiable()) {
            redirection.addFlashAttribute("erreur", "Ce dossier est figé.");
            return "redirect:/dossiers/" + id;
        }

        if (!sansAntecedent && (detail == null || detail.isBlank())) {
            redirection.addFlashAttribute("erreur",
                    "Déclarer des marchés non exécutés sans les détailler laisse le "
                            + "formulaire incomplet : la commission demandera lesquels.");
            return "redirect:/dossiers/" + id;
        }

        dossier.setSansAntecedent(sansAntecedent);
        dossier.setAntecedentsDetail(sansAntecedent ? null : detail);
        depotDossier.save(dossier);

        redirection.addFlashAttribute("message", "Déclaration enregistrée.");
        return "redirect:/dossiers/" + id;
    }

    // ------------------------------------------------------------------ moyens

    @PostMapping("/{id}/rapprochement")
    public String rapprocher(@PathVariable UUID id, RedirectAttributes redirection) {
        var resultat = rapprochement.proposer(id);
        moteur.executer(id);

        if (resultat.complet()) {
            redirection.addFlashAttribute("message",
                    "Rapprochement effectué : %d personne(s) et %d matériel(s) proposés. "
                            .formatted(resultat.personnesProposees(), resultat.materielsProposes())
                            + "Vérifiez les propositions avant de générer.");
        } else {
            redirection.addFlashAttribute("erreur",
                    "Exigences non pourvues : "
                            + String.join(", ", resultat.exigencesNonPourvues())
                            + ". Complétez le personnel ou le matériel.");
        }
        return "redirect:/dossiers/" + id;
    }

    // ------------------------------------------------------------------ circuit

    @PostMapping("/{id}/generer")
    public String generer(@PathVariable UUID id, RedirectAttributes redirection) {
        try {
            var documents = generation.genererTout(id, false);
            redirection.addFlashAttribute("message",
                    documents.size() + " document(s) générés, filigranés BROUILLON.");
        } catch (RuntimeException erreur) {
            redirection.addFlashAttribute("erreur",
                    "Génération impossible : " + erreur.getMessage());
        }
        return "redirect:/dossiers/" + id;
    }

    @PostMapping("/{id}/soumettre")
    public String soumettre(@PathVariable UUID id, RedirectAttributes redirection) {
        try {
            serviceDossier.soumettreAValidation(id);
            redirection.addFlashAttribute("message", "Dossier soumis à validation.");
        } catch (ControleBloquantException erreur) {
            redirection.addFlashAttribute("erreur", erreur.getMessage());
        } catch (DossierFigeException erreur) {
            redirection.addFlashAttribute("erreur", erreur.getMessage());
        }
        return "redirect:/dossiers/" + id;
    }

    @GetMapping("/{id}/validation")
    public String validation(@PathVariable UUID id, Model modele) {
        poserDossier(id, modele);
        modele.addAttribute("rapport", moteur.dernierRapport(id));
        modele.addAttribute("documents",
                depotDocumentGenere.findAllByDossierIdOrderByGenereLeDesc(id));
        return "dossier/validation";
    }

    @PostMapping("/{id}/validation/valider")
    public String valider(@PathVariable UUID id,
                          @RequestParam(required = false) String commentaire,
                          RedirectAttributes redirection) {
        try {
            serviceDossier.valider(id, commentaire);
            // Les documents sont regeneres sans filigrane : ce sont eux qui
            // portent l'empreinte de la version signee.
            generation.genererTout(id, true);
            redirection.addFlashAttribute("message",
                    "Dossier validé et verrouillé. Les PDF définitifs sont disponibles.");
        } catch (SeparationDesTachesException | ControleBloquantException
                 | IllegalStateException erreur) {
            redirection.addFlashAttribute("erreur", erreur.getMessage());
        }
        return "redirect:/dossiers/" + id;
    }

    @PostMapping("/{id}/validation/renvoyer")
    public String renvoyer(@PathVariable UUID id,
                           @RequestParam String commentaire,
                           RedirectAttributes redirection) {
        try {
            serviceDossier.renvoyerEnBrouillon(id, commentaire);
            redirection.addFlashAttribute("message", "Dossier renvoyé en brouillon.");
        } catch (IllegalArgumentException | IllegalStateException erreur) {
            redirection.addFlashAttribute("erreur", erreur.getMessage());
        }
        return "redirect:/dossiers/" + id;
    }

    @PostMapping("/{id}/depose")
    public String marquerDepose(@PathVariable UUID id, RedirectAttributes redirection) {
        try {
            serviceDossier.marquerDepose(id);
            redirection.addFlashAttribute("message", "Dossier marqué comme déposé.");
        } catch (IllegalStateException erreur) {
            redirection.addFlashAttribute("erreur", erreur.getMessage());
        }
        return "redirect:/dossiers/" + id;
    }

    @PostMapping("/{id}/nouvelle-version")
    public String nouvelleVersion(@PathVariable UUID id, RedirectAttributes redirection) {
        try {
            Dossier suivant = serviceDossier.nouvelleVersion(id);
            redirection.addFlashAttribute("message",
                    "Version " + suivant.getVersion() + " créée. La précédente reste intacte.");
            return "redirect:/dossiers/" + suivant.getId();
        } catch (IllegalStateException erreur) {
            redirection.addFlashAttribute("erreur", erreur.getMessage());
            return "redirect:/dossiers/" + id;
        }
    }

    // ------------------------------------------------------------------ telechargements

    /**
     * Les trois documents, telecharges tels quels.
     *
     * <p>Rien n'est enregistre : l'utilisateur veut son PDF, pas une trace de
     * plus en base. Les versions archivees, elles, naissent a la validation -
     * ce sont elles qui portent une empreinte, parce qu'elles seules sont
     * signees.
     */
    @GetMapping("/{id}/offre-technique.pdf")
    public ResponseEntity<ByteArrayResource> offreTechnique(@PathVariable UUID id) {
        return pdf(id, DocumentGenere.Type.TECHNIQUE, "Offre technique");
    }

    @GetMapping("/{id}/offre-financiere.pdf")
    public ResponseEntity<ByteArrayResource> offreFinanciere(@PathVariable UUID id) {
        return pdf(id, DocumentGenere.Type.FINANCIERE, "Offre financiere");
    }

    @GetMapping("/{id}/rapport-conformite.pdf")
    public ResponseEntity<ByteArrayResource> rapportConformite(@PathVariable UUID id) {
        return pdf(id, DocumentGenere.Type.RAPPORT_CONFORMITE, "Rapport de conformite");
    }

    private ResponseEntity<ByteArrayResource> pdf(UUID dossierId,
                                                  DocumentGenere.Type type,
                                                  String libelle) {
        Dossier dossier = serviceDossier.charger(dossierId);
        byte[] contenu = generation.produireMaintenant(dossierId, type);

        String nom = "%s - v%d%s.pdf".formatted(libelle, dossier.getVersion(),
                dossier.porteFiligrane() ? " - BROUILLON" : "");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, dispositionAttachement(nom))
                .body(new ByteArrayResource(contenu));
    }

    // ------------------------------------------------------------------ archive

    @GetMapping("/{id}/archive")
    @Transactional(readOnly = true)
    public ResponseEntity<ByteArrayResource> telecharger(@PathVariable UUID id) {
        Dossier dossier = serviceDossier.charger(id);
        String intitule = depotAppelOffres.findById(dossier.getAppelOffresId())
                .map(AppelOffres::getIntitule)
                .orElse("dossier");

        byte[] zip = archive.archiverDepuisLeStockage(id);
        String nom = archive.nomDeFichier(dossier, intitule);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, dispositionAttachement(nom))
                .body(new ByteArrayResource(zip));
    }

    // ------------------------------------------------------------------ interne

    private void poserDossier(UUID id, Model modele) {
        Dossier dossier = serviceDossier.charger(id);
        AppelOffres appel = depotAppelOffres.findById(dossier.getAppelOffresId())
                .orElseThrow(() -> new IllegalStateException("Appel d'offres introuvable."));

        modele.addAttribute("rubrique", "dossiers");
        modele.addAttribute("titre", appel.getIntitule());
        modele.addAttribute("sousTitre", dossier.getLotId() == null
                ? "Marché non alloti"
                : "Version " + dossier.getVersion());
        modele.addAttribute("dossier", dossier);
        modele.addAttribute("appel", appel);
        modele.addAttribute("lot", dossier.getLotId() == null
                ? null : depotLot.findById(dossier.getLotId()).orElse(null));
    }

    /**
     * Les noms de fichiers francais contiennent des accents : sans l'encodage
     * RFC 5987, le navigateur recoit des caracteres abimes.
     */
    private String dispositionAttachement(String nom) {
        String sansAccents = java.text.Normalizer.normalize(nom, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String encode = java.net.URLEncoder.encode(nom, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "attachment; filename=\"" + sansAccents + "\"; filename*=UTF-8''" + encode;
    }
}
