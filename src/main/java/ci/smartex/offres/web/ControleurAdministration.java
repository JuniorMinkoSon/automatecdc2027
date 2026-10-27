package ci.smartex.offres.web;

import ci.smartex.offres.domain.DemandeInscription;
import ci.smartex.offres.domain.Paiement;
import ci.smartex.offres.repo.DepotDemandeInscription;
import ci.smartex.offres.repo.DepotOrganisation;
import ci.smartex.offres.repo.DepotPaiement;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * L'administration de la plateforme.
 *
 * <p>Le super-administrateur voit les comptes et les paiements, jamais les
 * dossiers des clients. Ce n'est pas qu'une regle d'interface : aucune
 * politique de la base ne lui ouvre les tables de dossiers, donc une requete
 * qui essaierait ne renverrait rien.
 */
@Controller
@RequestMapping("/administration")
@Secured("ROLE_SUPER_ADMIN")
public class ControleurAdministration {

    private final DepotDemandeInscription depotDemandes;
    private final DepotOrganisation depotOrganisations;
    private final DepotPaiement depotPaiements;

    public ControleurAdministration(DepotDemandeInscription depotDemandes,
                                    DepotOrganisation depotOrganisations,
                                    DepotPaiement depotPaiements) {
        this.depotDemandes = depotDemandes;
        this.depotOrganisations = depotOrganisations;
        this.depotPaiements = depotPaiements;
    }

    @GetMapping
    public String afficher(Model modele) {
        modele.addAttribute("rubrique", "administration");
        modele.addAttribute("titre", "Administration de la plateforme");
        modele.addAttribute("sousTitre",
                "Les comptes et les paiements. Jamais les dossiers des clients.");
        modele.addAttribute("demandes", depotDemandes.findAllByOrderByCreeLeDesc());
        modele.addAttribute("organisations", depotOrganisations.findAll());
        modele.addAttribute("paiements",
                depotPaiements.findAllByStatutOrderByCreeLeDesc(Paiement.Statut.CONFIRME));
        modele.addAttribute("statuts", DemandeInscription.Statut.values());
        return "admin/console";
    }
}
