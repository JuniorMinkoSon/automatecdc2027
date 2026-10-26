package ci.smartex.offres.securite;

import ci.smartex.offres.domain.Mandat;
import ci.smartex.offres.domain.Membre;
import ci.smartex.offres.repo.DepotMandat;
import ci.smartex.offres.repo.DepotMembre;
import ci.smartex.offres.repo.DepotUtilisateur;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Charge un compte et ses appartenances au moment de la connexion.
 *
 * <p>Le mandat est verifie ici : un operateur de l'equipe ne recupere
 * l'organisation d'un client que tant que ce client lui a laisse le mandat.
 * Le retirer suffit donc a lui fermer la porte, sans passer par un
 * administrateur.
 */
@Service
public class ServiceDetailsUtilisateur implements UserDetailsService {

    private final DepotUtilisateur depotUtilisateur;
    private final DepotMembre depotMembre;
    private final DepotMandat depotMandat;

    public ServiceDetailsUtilisateur(DepotUtilisateur depotUtilisateur,
                                     DepotMembre depotMembre,
                                     DepotMandat depotMandat) {
        this.depotUtilisateur = depotUtilisateur;
        this.depotMembre = depotMembre;
        this.depotMandat = depotMandat;
    }

    @Override
    @Transactional(readOnly = true)
    public UtilisateurConnecte loadUserByUsername(String email) {
        var utilisateur = depotUtilisateur.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Compte inconnu."));

        if (!utilisateur.aChoisiSonMotDePasse()) {
            throw new UsernameNotFoundException(
                    "Ce compte n'a pas encore ete active par son invitation.");
        }
        if (utilisateur.estVerrouille()) {
            throw new CompteVerrouilleException(
                    "Compte verrouille apres plusieurs echecs. Reessayez plus tard.");
        }

        Map<UUID, Membre.Role> appartenances = new HashMap<>();
        for (Membre membre : depotMembre.findAllByUtilisateurId(utilisateur.getId())) {
            if (membre.getRole() == Membre.Role.OPERATEUR_MANDATE
                    && !mandatEnCours(membre.getOrganisationId())) {
                continue;
            }
            // Un utilisateur a au plus un role par organisation ; si la base en
            // porte plusieurs, le plus restrictif ne doit pas effacer l'autre :
            // on garde le premier lu, et la contrainte d'unicite evite le cas.
            appartenances.putIfAbsent(membre.getOrganisationId(), membre.getRole());
        }

        return new UtilisateurConnecte(
                utilisateur.getId(),
                utilisateur.getEmail(),
                utilisateur.getNomComplet(),
                utilisateur.getMotDePasseHash(),
                utilisateur.isActif(),
                utilisateur.isSuperAdmin(),
                utilisateur.isTotpActif(),
                appartenances);
    }

    /**
     * La table mandat est hors Row-Level Security, parce qu'elle fait partie de
     * l'annuaire des droits et se lit avant toute organisation (voir V2). Le
     * filtrage par organisation est donc explicite ici.
     */
    private boolean mandatEnCours(UUID organisationId) {
        return depotMandat.findFirstByOrganisationIdAndRetireLeIsNull(organisationId)
                .map(Mandat::estEnCours)
                .orElse(false);
    }
}
