package ci.smartex.offres.securite;

import ci.smartex.offres.domain.Membre;
import ci.smartex.offres.repo.DepotUtilisateur;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Mot de passe, puis second facteur, puis compteur d'echecs.
 *
 * <p>Le second facteur est exige des quatre roles qui engagent l'entreprise ou
 * touchent a l'argent. Pour un lecteur ou un redacteur, il reste facultatif :
 * l'imposer a tout le monde pousse au contournement sans rien proteger de
 * decisif.
 */
@Component
public class FournisseurAuthentification extends DaoAuthenticationProvider {

    /** Les roles pour lesquels le second facteur n'est pas negociable. */
    private static final Set<Membre.Role> DEUXIEME_FACTEUR_OBLIGATOIRE = Set.of(
            Membre.Role.ADMIN_ENTREPRISE,
            Membre.Role.VALIDATEUR,
            Membre.Role.OPERATEUR_MANDATE);

    private static final int ECHECS_AVANT_VERROU = 5;
    private static final int MINUTES_DE_VERROU = 15;

    private final DepotUtilisateur depotUtilisateur;

    public FournisseurAuthentification(ServiceDetailsUtilisateur serviceDetails,
                                       PasswordEncoder encodeur,
                                       DepotUtilisateur depotUtilisateur) {
        this.depotUtilisateur = depotUtilisateur;
        setUserDetailsService(serviceDetails);
        setPasswordEncoder(encodeur);
        // Ne pas cacher l'absence de compte derriere un mot de passe faux :
        // le message rendu a l'ecran reste le meme dans les deux cas.
        setHideUserNotFoundExceptions(true);
    }

    @Override
    protected void additionalAuthenticationChecks(UserDetails details,
                                                  UsernamePasswordAuthenticationToken jeton) {
        try {
            super.additionalAuthenticationChecks(details, jeton);
        } catch (BadCredentialsException erreur) {
            enregistrerEchec(details);
            throw erreur;
        }

        if (details instanceof UtilisateurConnecte utilisateur) {
            verifierSecondFacteur(utilisateur, jeton);
        }
        remettreCompteurAZero(details.getUsername());
    }

    private void verifierSecondFacteur(UtilisateurConnecte utilisateur,
                                       UsernamePasswordAuthenticationToken jeton) {
        boolean obligatoire = utilisateur.isSuperAdmin()
                || utilisateur.getAppartenances().values().stream()
                        .anyMatch(DEUXIEME_FACTEUR_OBLIGATOIRE::contains);

        if (!obligatoire && !utilisateur.isTotpActif()) {
            return;
        }

        if (!utilisateur.isTotpActif()) {
            // Le role exige le second facteur mais le compte ne l'a pas encore
            // configure : la connexion passe, et la page suivante force
            // l'enrolement avant toute autre action.
            return;
        }

        String code = jeton.getDetails() instanceof DetailsAuthentificationAvecCode details
                ? details.getCodeUnique()
                : null;

        String secret = depotUtilisateur.findById(utilisateur.getUtilisateurId())
                .map(u -> u.getTotpSecret())
                .orElse(null);

        if (!Totp.verifier(secret, code)) {
            enregistrerEchec(utilisateur);
            throw new CodeDeuxFacteursInvalideException("Identifiants invalides.");
        }
    }

    /**
     * Cinq echecs verrouillent le compte un quart d'heure. Le compteur porte
     * sur le compte et non sur l'adresse : un attaquant qui change d'adresse
     * ne repart pas de zero.
     */
    @Transactional
    protected void enregistrerEchec(UserDetails details) {
        depotUtilisateur.findByEmailIgnoreCase(details.getUsername()).ifPresent(utilisateur -> {
            int echecs = utilisateur.getEchecsConnexion() + 1;
            utilisateur.setEchecsConnexion(echecs);
            if (echecs >= ECHECS_AVANT_VERROU) {
                utilisateur.setVerrouilleJusquA(Instant.now().plus(MINUTES_DE_VERROU, ChronoUnit.MINUTES));
                utilisateur.setEchecsConnexion(0);
            }
            depotUtilisateur.save(utilisateur);
        });
    }

    @Transactional
    protected void remettreCompteurAZero(String email) {
        depotUtilisateur.findByEmailIgnoreCase(email).ifPresent(utilisateur -> {
            if (utilisateur.getEchecsConnexion() != 0 || utilisateur.getVerrouilleJusquA() != null) {
                utilisateur.setEchecsConnexion(0);
                utilisateur.setVerrouilleJusquA(null);
                depotUtilisateur.save(utilisateur);
            }
        });
    }
}
