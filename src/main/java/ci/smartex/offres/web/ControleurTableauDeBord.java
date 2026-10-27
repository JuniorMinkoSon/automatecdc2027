package ci.smartex.offres.web;

import ci.smartex.offres.config.ProprietesApp;
import ci.smartex.offres.domain.AppelOffres;
import ci.smartex.offres.domain.Dossier;
import ci.smartex.offres.domain.PieceAdmin;
import ci.smartex.offres.repo.DepotAppelOffres;
import ci.smartex.offres.repo.DepotDossier;
import ci.smartex.offres.repo.DepotPieceAdmin;
import ci.smartex.offres.securite.ContexteOrganisation;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Le tableau de bord repond a une seule question : qu'est-ce qui bloque un
 * depot aujourd'hui ?
 *
 * <p>Il ne montre donc ni courbe ni total cumule. Ce qui compte, c'est ce qui
 * a une echeance et ce qui va perimer, parce que ce sont les deux choses qui
 * font perdre un marche sans qu'on les ait vues venir.
 */
@Controller
public class ControleurTableauDeBord {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final DepotDossier depotDossier;
    private final DepotAppelOffres depotAppelOffres;
    private final DepotPieceAdmin depotPieceAdmin;
    private final ProprietesApp proprietes;

    public ControleurTableauDeBord(DepotDossier depotDossier,
                                   DepotAppelOffres depotAppelOffres,
                                   DepotPieceAdmin depotPieceAdmin,
                                   ProprietesApp proprietes) {
        this.depotDossier = depotDossier;
        this.depotAppelOffres = depotAppelOffres;
        this.depotPieceAdmin = depotPieceAdmin;
        this.proprietes = proprietes;
    }

    @GetMapping("/tableau-de-bord")
    public String afficher(Model modele) {
        UUID organisation = ContexteOrganisation.organisationCourante().orElse(null);
        if (organisation == null) {
            // Un super-administrateur n'a pas d'organisation : sa place est
            // dans l'administration, pas devant des dossiers qu'il ne doit
            // pas voir.
            return "redirect:/administration";
        }

        List<Dossier> dossiers = depotDossier.findAllByOrganisationIdOrderByMajLeDesc(organisation);
        Map<UUID, AppelOffres> appels = depotAppelOffres
                .findAllByOrganisationIdOrderByCreeLeDesc(organisation).stream()
                .collect(Collectors.toMap(AppelOffres::getId, Function.identity()));

        LocalDate aujourdhui = LocalDate.now(ABIDJAN);
        Instant dansSeptJours = Instant.now().plus(7, ChronoUnit.DAYS);

        List<LigneDossier> lignes = dossiers.stream()
                .filter(dossier -> dossier.getStatut() != Dossier.Statut.DEPOSE)
                .map(dossier -> ligne(dossier, appels.get(dossier.getAppelOffresId())))
                .toList();

        List<PieceAdmin> aRenouveler = depotPieceAdmin.findAllByOrganisationId(organisation)
                .stream()
                .filter(piece -> piece.getDateExpiration() != null)
                .filter(piece -> piece.getDateExpiration()
                        .isBefore(aujourdhui.plusDays(proprietes.getVeille().getJoursAvantExpiration())))
                .sorted((a, b) -> a.getDateExpiration().compareTo(b.getDateExpiration()))
                .toList();

        long echeancesProches = lignes.stream()
                .filter(ligne -> ligne.dateLimite() != null)
                .filter(ligne -> ligne.dateLimite().isBefore(dansSeptJours))
                .count();

        modele.addAttribute("rubrique", "tableau");
        modele.addAttribute("titre", "Tableau de bord");
        modele.addAttribute("sousTitre",
                "Ce qui peut empêcher un dépôt aujourd'hui.");
        modele.addAttribute("dossiers", lignes);
        modele.addAttribute("piecesARenouveler", aRenouveler);
        modele.addAttribute("nbDossiers", lignes.size());
        modele.addAttribute("nbEcheances", echeancesProches);
        modele.addAttribute("nbPieces", aRenouveler.size());
        modele.addAttribute("aujourdhui", aujourdhui);
        return "tableau-de-bord";
    }

    private LigneDossier ligne(Dossier dossier, AppelOffres appel) {
        return new LigneDossier(
                dossier.getId(),
                appel == null ? "Appel d'offres inconnu" : appel.getIntitule(),
                dossier.getLotId() == null ? "—" : "Lot",
                dossier.getStatut(),
                dossier.getVersion(),
                appel == null ? null : appel.getDateLimiteDepot());
    }

    /** Ce qu'une ligne du tableau affiche, sans aller rechercher en base a l'ecran. */
    public record LigneDossier(UUID id,
                               String intitule,
                               String lot,
                               Dossier.Statut statut,
                               int version,
                               Instant dateLimite) {

        public String classeEtat() {
            return switch (statut) {
                case BROUILLON -> "brouillon";
                case EN_VALIDATION -> "validation";
                case VALIDE -> "valide";
                case DEPOSE -> "depose";
            };
        }

        public String libelleEtat() {
            return switch (statut) {
                case BROUILLON -> "Brouillon";
                case EN_VALIDATION -> "En validation";
                case VALIDE -> "Validé";
                case DEPOSE -> "Déposé";
            };
        }

        /** Le DAO de reference laissait la date limite en blanc : il faut pouvoir le dire. */
        public String echeance() {
            if (dateLimite == null) {
                return "Date limite à compléter";
            }
            LocalDate jour = dateLimite.atZone(ABIDJAN).toLocalDate();
            long jours = ChronoUnit.DAYS.between(LocalDate.now(ABIDJAN), jour);
            if (jours < 0) {
                return "Dépassée le " + jour;
            }
            if (jours == 0) {
                return "Aujourd'hui";
            }
            return jour + " (J-" + jours + ")";
        }

        public boolean urgente() {
            if (dateLimite == null) {
                return false;
            }
            long jours = ChronoUnit.DAYS.between(LocalDate.now(ABIDJAN),
                    dateLimite.atZone(ABIDJAN).toLocalDate());
            return jours <= 3;
        }
    }
}
