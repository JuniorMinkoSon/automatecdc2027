package ci.smartex.offres.jobs;

import ci.smartex.offres.config.ProprietesApp;
import ci.smartex.offres.domain.Dossier;
import ci.smartex.offres.domain.Membre;
import ci.smartex.offres.domain.PieceAdmin;
import ci.smartex.offres.repo.DepotMembre;
import ci.smartex.offres.repo.DepotUtilisateur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Envoie les avertissements de la veille quotidienne.
 *
 * <p>Un envoi qui echoue est journalise et depasse. Le courrier est un confort :
 * les memes alertes sont sur le tableau de bord, qui lui ne depend d'aucun
 * service tiers. Faire echouer la veille parce qu'un serveur SMTP ne repond
 * pas priverait l'entreprise des alertes suivantes, ce qui serait exactement
 * le contraire du but.
 *
 * <p>Les destinataires sont les administrateurs et les validateurs de
 * l'organisation : ce sont eux qui peuvent agir. Prevenir un lecteur qui ne
 * peut rien corriger ne sert qu'a diluer l'attention.
 */
@Service
public class ServiceNotification {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceNotification.class);

    private final JavaMailSender courrier;
    private final DepotMembre depotMembre;
    private final DepotUtilisateur depotUtilisateur;
    private final ProprietesApp proprietes;

    public ServiceNotification(JavaMailSender courrier,
                               DepotMembre depotMembre,
                               DepotUtilisateur depotUtilisateur,
                               ProprietesApp proprietes) {
        this.courrier = courrier;
        this.depotMembre = depotMembre;
        this.depotUtilisateur = depotUtilisateur;
        this.proprietes = proprietes;
    }

    public void piecesARenouveler(UUID organisationId,
                                  List<PieceAdmin> concernees,
                                  List<PieceAdmin> perimees) {

        StringBuilder corps = new StringBuilder();
        corps.append("Des pièces de votre coffre arrivent à expiration.\n\n");

        if (!perimees.isEmpty()) {
            corps.append("DÉJÀ PÉRIMÉES — à remplacer avant le prochain dépôt :\n");
            perimees.forEach(piece -> corps.append("  - ")
                    .append(piece.getType())
                    .append(", expirée le ").append(piece.getDateExpiration())
                    .append('\n'));
            corps.append('\n');
        }

        List<PieceAdmin> aVenir = concernees.stream()
                .filter(piece -> !perimees.contains(piece))
                .toList();
        if (!aVenir.isEmpty()) {
            corps.append("À renouveler prochainement :\n");
            aVenir.forEach(piece -> corps.append("  - ")
                    .append(piece.getType())
                    .append(", expire le ").append(piece.getDateExpiration())
                    .append('\n'));
            corps.append('\n');
        }

        corps.append("Une pièce périmée à la date limite de dépôt fait échouer un\n");
        corps.append("contrôle bloquant : le dossier ne pourra pas être validé.\n\n");
        corps.append(proprietes.getUrlPublique()).append("/coffre\n");

        envoyer(organisationId, "Pièces à renouveler", corps.toString());
    }

    public void echeanceProche(UUID organisationId,
                               Dossier dossier,
                               String intituleMarche,
                               int joursRestants) {

        String corps = """
                Un dossier n'est pas encore déposé et la date limite approche.

                Marché  : %s
                Version : %d
                Statut  : %s
                Reste   : %d jour(s)

                %s/dossiers/%s
                """.formatted(intituleMarche, dossier.getVersion(), dossier.getStatut(),
                        joursRestants, proprietes.getUrlPublique(), dossier.getId());

        envoyer(organisationId, "Dépôt dans " + joursRestants + " jour(s)", corps);
    }

    public void abonnementArriveAEcheance(UUID organisationId, long joursRestants) {
        String corps = """
                Votre abonnement arrive à échéance dans %d jour(s).

                À l'expiration, l'accès passe en lecture seule : vos dossiers
                restent téléchargeables, mais aucun nouveau dossier ne peut être
                préparé. Rien n'est supprimé.

                %s
                """.formatted(joursRestants, proprietes.getUrlPublique());

        envoyer(organisationId, "Abonnement à renouveler", corps);
    }

    // ------------------------------------------------------------------ envoi

    private void envoyer(UUID organisationId, String objet, String corps) {
        List<String> destinataires = destinataires(organisationId);
        if (destinataires.isEmpty()) {
            LOG.warn("Aucun destinataire pour l'organisation {} : alerte « {} » non envoyée.",
                    organisationId, objet);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(destinataires.toArray(String[]::new));
        message.setSubject(objet);
        message.setText(corps);

        try {
            courrier.send(message);
        } catch (Exception erreur) {
            // Les memes alertes sont sur le tableau de bord : l'echec d'un
            // envoi ne doit pas interrompre la veille des autres organisations.
            LOG.warn("Envoi de « {} » impossible pour l'organisation {} : {}",
                    objet, organisationId, erreur.getMessage());
        }
    }

    /** Ceux qui peuvent corriger : administrateurs et validateurs. */
    private List<String> destinataires(UUID organisationId) {
        return depotMembre.findAllByOrganisationId(organisationId).stream()
                .filter(membre -> membre.getRole() == Membre.Role.ADMIN_ENTREPRISE
                        || membre.getRole() == Membre.Role.VALIDATEUR)
                .map(Membre::getUtilisateurId)
                .distinct()
                .map(depotUtilisateur::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(utilisateur -> utilisateur.isActif())
                .map(utilisateur -> utilisateur.getEmail())
                .collect(Collectors.toList());
    }
}
