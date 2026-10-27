package ci.smartex.offres.web;

import ci.smartex.offres.repo.DepotEntreprise;
import ci.smartex.offres.securite.ContexteOrganisation;
import ci.smartex.offres.securite.UtilisateurConnecte;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Ce que tous les ecrans affichent : le nom de l'entreprise et le role actif.
 *
 * <p>Pose ici une fois plutot que dans chaque controleur. Les deux valeurs
 * viennent de la session et du contexte d'organisation, jamais d'un parametre
 * de la requete.
 */
@ControllerAdvice(assignableTypes = {
        ControleurTableauDeBord.class,
        ControleurAppelOffres.class,
        ControleurPublication.class,
        ControleurDossier.class,
        ControleurCoffre.class,
        ControleurModeles.class,
        ControleurAdministration.class
})
public class ContexteDesEcrans {

    private final DepotEntreprise depotEntreprise;

    public ContexteDesEcrans(DepotEntreprise depotEntreprise) {
        this.depotEntreprise = depotEntreprise;
    }

    @ModelAttribute
    public void poser(Model modele, Authentication authentification) {
        if (authentification == null
                || !(authentification.getPrincipal() instanceof UtilisateurConnecte utilisateur)) {
            return;
        }

        utilisateur.roleActif().ifPresent(role -> modele.addAttribute("roleActif", libelle(role)));

        if (utilisateur.isSuperAdmin()) {
            modele.addAttribute("nomEntreprise", "Administration de la plateforme");
            return;
        }

        ContexteOrganisation.organisationCourante().ifPresent(organisation ->
                depotEntreprise.findByOrganisationId(organisation).ifPresent(entreprise ->
                        modele.addAttribute("nomEntreprise", entreprise.denominationCourte())));
    }

    private String libelle(ci.smartex.offres.domain.Membre.Role role) {
        return switch (role) {
            case ADMIN_ENTREPRISE -> "Administrateur";
            case REDACTEUR -> "Rédacteur";
            case VALIDATEUR -> "Validateur";
            case LECTEUR -> "Lecteur";
            case OPERATEUR_MANDATE -> "Opérateur mandaté";
        };
    }
}
