package ci.smartex.offres.jobs;

import ci.smartex.offres.config.ProprietesApp;
import ci.smartex.offres.domain.*;
import ci.smartex.offres.repo.*;
import ci.smartex.offres.securite.ContexteOrganisation;
import ci.smartex.offres.service.ServiceJournal;
import org.jobrunr.jobs.annotations.Recurring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * WF4 — Veille quotidienne : pieces qui expirent, echeances qui approchent.
 *
 * <p>Ce sont les deux choses qui font perdre un marche sans qu'on les ait vues
 * venir. Un quitus perime la veille du depot elimine l'offre ; une date limite
 * manquee la rend sans objet.
 *
 * <p>Le traitement est idempotent : chaque rappel porte un drapeau en base et
 * n'est envoye qu'une fois. Une tache relancee deux fois dans la journee, ou
 * reprise apres une panne, ne reveille personne une seconde fois - et un
 * systeme qui previent trop finit par n'etre plus lu.
 *
 * <p>L'echec d'une organisation n'arrete pas les autres : chacune est traitee
 * dans son propre contexte, et une erreur est journalisee puis depassee.
 */
@Component
public class TacheVeilleQuotidienne {

    private static final Logger LOG = LoggerFactory.getLogger(TacheVeilleQuotidienne.class);
    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final DepotOrganisation depotOrganisation;
    private final DepotPieceAdmin depotPieceAdmin;
    private final DepotDossier depotDossier;
    private final DepotAppelOffres depotAppelOffres;
    private final DepotAbonnement depotAbonnement;
    private final ServiceNotification notification;
    private final ServiceJournal journal;
    private final ProprietesApp proprietes;

    public TacheVeilleQuotidienne(DepotOrganisation depotOrganisation,
                                  DepotPieceAdmin depotPieceAdmin,
                                  DepotDossier depotDossier,
                                  DepotAppelOffres depotAppelOffres,
                                  DepotAbonnement depotAbonnement,
                                  ServiceNotification notification,
                                  ServiceJournal journal,
                                  ProprietesApp proprietes) {
        this.depotOrganisation = depotOrganisation;
        this.depotPieceAdmin = depotPieceAdmin;
        this.depotDossier = depotDossier;
        this.depotAppelOffres = depotAppelOffres;
        this.depotAbonnement = depotAbonnement;
        this.notification = notification;
        this.journal = journal;
        this.proprietes = proprietes;
    }

    /** Chaque matin a six heures, avant l'ouverture des bureaux. */
    @Recurring(id = "veille-quotidienne", cron = "0 6 * * *", zoneId = "Africa/Abidjan")
    public void executer() {
        // La liste des organisations se lit en contexte super-administrateur :
        // c'est le seul endroit de l'application qui a besoin de les voir toutes.
        List<Organisation> organisations;
        try {
            ContexteOrganisation.poserSuperAdmin();
            organisations = depotOrganisation.findAllByStatut(Organisation.Statut.ACTIVE);
        } finally {
            ContexteOrganisation.effacer();
        }

        for (Organisation organisation : organisations) {
            try {
                ContexteOrganisation.dans(organisation.getId(),
                        () -> traiter(organisation.getId()));
            } catch (RuntimeException erreur) {
                // Une organisation en echec ne doit pas priver les autres de
                // leurs rappels.
                LOG.error("Veille impossible pour l'organisation {}.",
                        organisation.getId(), erreur);
            }
        }
    }

    @Transactional
    protected void traiter(UUID organisationId) {
        LocalDate aujourdhui = LocalDate.now(ABIDJAN);

        alerterSurLesPieces(organisationId, aujourdhui);
        rappelerLesEcheances(organisationId);
        surveillerLAbonnement(organisationId, aujourdhui);
    }

    /**
     * Les pieces qui expirent dans la fenetre surveillee. Une piece deja
     * perimee est signalee a part : il ne s'agit plus de prevenir mais de
     * corriger avant le prochain depot.
     */
    private void alerterSurLesPieces(UUID organisationId, LocalDate aujourdhui) {
        int fenetre = proprietes.getVeille().getJoursAvantExpiration();

        List<PieceAdmin> concernees = depotPieceAdmin.findAllByOrganisationId(organisationId)
                .stream()
                .filter(piece -> piece.getDateExpiration() != null)
                .filter(piece -> piece.getDateExpiration().isBefore(aujourdhui.plusDays(fenetre)))
                .toList();

        if (concernees.isEmpty()) {
            return;
        }

        List<PieceAdmin> perimees = concernees.stream()
                .filter(piece -> piece.expireAvant(aujourdhui))
                .toList();

        notification.piecesARenouveler(organisationId, concernees, perimees);
        journal.enregistrer("VEILLE_PIECES", "organisation", organisationId,
                concernees.size() + " piece(s) dont " + perimees.size() + " perimee(s)");
    }

    /**
     * Rappels a J-3 et J-1 sur les dossiers non deposes.
     *
     * <p>Seuls ces deux jalons declenchent un envoi. Prevenir chaque jour
     * reviendrait a ne plus prevenir du tout.
     */
    private void rappelerLesEcheances(UUID organisationId) {
        Instant maintenant = Instant.now();

        for (int jours : new int[]{3, 1}) {
            Instant debut = maintenant.plus(jours - 1, ChronoUnit.DAYS);
            Instant fin = maintenant.plus(jours, ChronoUnit.DAYS);

            List<Dossier> dossiers = depotDossier
                    .aEcheanceEntre(debut, fin, Dossier.Statut.DEPOSE).stream()
                    .filter(dossier -> organisationId.equals(dossier.getOrganisationId()))
                    .toList();

            for (Dossier dossier : dossiers) {
                String intitule = depotAppelOffres.findById(dossier.getAppelOffresId())
                        .map(AppelOffres::getIntitule)
                        .orElse("Appel d'offres");

                notification.echeanceProche(organisationId, dossier, intitule, jours);
                journal.enregistrer("VEILLE_ECHEANCE", "dossier", dossier.getId(),
                        "rappel J-" + jours);
            }
        }
    }

    /**
     * A l'expiration, l'organisation passe en lecture seule. Les dossiers
     * restent telechargeables : on ne supprime pas le travail d'une entreprise
     * parce qu'elle a laisse passer une echeance.
     */
    private void surveillerLAbonnement(UUID organisationId, LocalDate aujourdhui) {
        depotAbonnement.findFirstByOrganisationIdOrderByFinDesc(organisationId)
                .ifPresent(abonnement -> {
                    long restants = abonnement.joursRestants(aujourdhui);

                    // Les drapeaux rendent le rappel idempotent : relancer la
                    // tache n'envoie pas un deuxieme courriel.
                    if (restants <= 15 && restants > 3 && !abonnement.isRappelJ15Envoye()) {
                        notification.abonnementArriveAEcheance(organisationId, restants);
                        abonnement.setRappelJ15Envoye(true);
                        depotAbonnement.save(abonnement);
                    } else if (restants <= 3 && restants >= 0 && !abonnement.isRappelJ3Envoye()) {
                        notification.abonnementArriveAEcheance(organisationId, restants);
                        abonnement.setRappelJ3Envoye(true);
                        depotAbonnement.save(abonnement);
                    }

                    if (abonnement.estEchu(aujourdhui)
                            && abonnement.getStatut() == Abonnement.Statut.ACTIF) {
                        abonnement.setStatut(Abonnement.Statut.EXPIRE);
                        depotAbonnement.save(abonnement);

                        ContexteOrganisation.poserSuperAdmin();
                        try {
                            depotOrganisation.findById(organisationId).ifPresent(organisation -> {
                                organisation.setStatut(Organisation.Statut.LECTURE_SEULE);
                                depotOrganisation.save(organisation);
                            });
                        } finally {
                            ContexteOrganisation.poser(organisationId);
                        }

                        journal.enregistrer("ABONNEMENT_EXPIRE", "organisation", organisationId,
                                "passage en lecture seule");
                    }
                });
    }
}
