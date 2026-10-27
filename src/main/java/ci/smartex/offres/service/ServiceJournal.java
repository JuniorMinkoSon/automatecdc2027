package ci.smartex.offres.service;

import ci.smartex.offres.domain.Journal;
import ci.smartex.offres.repo.DepotJournal;
import ci.smartex.offres.securite.ContexteOrganisation;
import ci.smartex.offres.securite.UtilisateurConnecte;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Journalise les actions sensibles : qui, quoi, quand, depuis quelle adresse.
 *
 * <p>L'ecriture se fait dans sa propre transaction. Une action annulee laisse
 * quand meme sa trace : c'est souvent la tentative qui interesse, pas seulement
 * ce qui a abouti.
 *
 * <p>En service cle en main, l'operateur est journalise comme operateur et non
 * comme l'entreprise : le client doit pouvoir distinguer, apres coup, ce qu'il
 * a saisi de ce que l'equipe a saisi pour lui.
 */
@Service
public class ServiceJournal {

    private final DepotJournal depot;

    public ServiceJournal(DepotJournal depot) {
        this.depot = depot;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enregistrer(String action, String objetType, Object objetId, String detail) {
        Journal ligne = new Journal();
        ligne.setOrganisationId(ContexteOrganisation.organisationCourante().orElse(null));
        ligne.setUtilisateurId(utilisateurCourant());
        ligne.setAction(action);
        ligne.setObjetType(objetType);
        ligne.setObjetId(objetId == null ? null : objetId.toString());
        ligne.setDetail(detail);
        ligne.setAdresseIp(adresseAppelante());
        depot.save(ligne);
    }

    public void enregistrer(String action, String objetType, Object objetId) {
        enregistrer(action, objetType, objetId, null);
    }

    private UUID utilisateurCourant() {
        var authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification != null
                && authentification.getPrincipal() instanceof UtilisateurConnecte utilisateur) {
            return utilisateur.getUtilisateurId();
        }
        return null;
    }

    /**
     * Derriere Caddy, l'adresse du client est dans X-Forwarded-For ; la
     * premiere valeur est celle du navigateur, les suivantes celles des relais.
     */
    private String adresseAppelante() {
        if (!(RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributs)) {
            // Une tache de fond n'a pas d'adresse appelante, et c'est normal.
            return null;
        }
        HttpServletRequest requete = attributs.getRequest();
        String transmise = requete.getHeader("X-Forwarded-For");
        if (transmise != null && !transmise.isBlank()) {
            return transmise.split(",")[0].trim();
        }
        return requete.getRemoteAddr();
    }
}
