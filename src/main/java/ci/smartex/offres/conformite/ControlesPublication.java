package ci.smartex.offres.conformite;

import ci.smartex.offres.domain.AppelOffres;
import ci.smartex.offres.domain.Lot;
import ci.smartex.offres.util.MontantEnLettres;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Ce qu'il faut verifier avant de publier un dossier d'appel d'offres.
 *
 * <p>Ces controles ne portent pas sur une offre mais sur le dossier lui-meme.
 * Ils existent parce qu'un dossier qui se contredit met les candidats dans
 * une situation impossible : ils redigent une offre non conforme sans avoir
 * commis d'erreur, et c'est l'autorite contractante qui recoltera les recours.
 *
 * <p>Les deux premiers sortent d'un avis reel. Il annoncait une garantie de
 * « cinq cent cinquante mille (850 000) francs CFA » - les lettres et les
 * chiffres disaient deux montants differents - et une validite des offres de
 * soixante jours quand le reglement en demandait cent vingt.
 *
 * <p>Classe autonome plutot que composants Spring : ces regles s'appliquent a
 * un appel d'offres en preparation, pas a un dossier de reponse, et le moteur
 * de conformite ne doit pas les executer sur les dossiers des soumissionnaires.
 */
public final class ControlesPublication {

    /** Delai minimal entre la publication et la date limite de depot. */
    private static final int JOURS_MINIMUM_DE_PUBLICITE = 30;

    private ControlesPublication() {
    }

    public static Rapport verifier(AppelOffres appel, List<Lot> lots) {
        List<Anomalie> anomalies = new ArrayList<>();

        coherenceDeValidite(appel, anomalies);
        garantieChiffree(lots, anomalies);
        calendrier(appel, anomalies);
        champsIndispensables(appel, lots, anomalies);

        return new Rapport(anomalies);
    }

    /**
     * L'avis et le reglement doivent annoncer la meme duree de validite.
     *
     * <p>Un candidat qui lit l'avis engage son offre pour la duree qu'il y
     * trouve. Si le reglement en exige une autre, son offre est ecartee pour
     * une contradiction dont il n'est pas l'auteur.
     */
    private static void coherenceDeValidite(AppelOffres appel, List<Anomalie> anomalies) {
        Integer annoncee = appel.getValiditeAnnonceeJours();
        Integer reglement = appel.getValiditeOffreJours();

        if (annoncee == null || reglement == null) {
            return;
        }
        if (!annoncee.equals(reglement)) {
            anomalies.add(Anomalie.bloquante(
                    "Durée de validité contradictoire",
                    "L'avis annonce %d jours, le règlement en exige %d. Un candidat qui "
                            .formatted(annoncee, reglement)
                            + "suit l'avis verra son offre écartée pour une contradiction "
                            + "qu'il n'a pas commise."));
        }
    }

    /**
     * Chaque lot doit porter un montant de garantie chiffre.
     *
     * <p>Dans le dossier genere, le montant en lettres et le montant en
     * chiffres sortent du meme nombre : l'ecart que l'avis de reference
     * presentait - « cinq cent cinquante mille (850 000) » - devient
     * impossible. Reste a verifier que le nombre existe.
     */
    private static void garantieChiffree(List<Lot> lots, List<Anomalie> anomalies) {
        List<String> sansGarantie = lots.stream()
                .filter(lot -> lot.getMontantGarantie() == null)
                .map(lot -> "lot " + lot.getNumero())
                .toList();

        if (!sansGarantie.isEmpty()) {
            anomalies.add(Anomalie.bloquante(
                    "Garantie non chiffrée",
                    "Aucun montant de cautionnement pour : "
                            + String.join(", ", sansGarantie)
                            + ". L'avis ne peut pas l'annoncer en lettres s'il n'est pas chiffré."));
        }

        lots.stream()
                .filter(lot -> lot.getMontantGarantie() != null && lot.getMontantGarantie() == 0)
                .findFirst()
                .ifPresent(lot -> anomalies.add(Anomalie.bloquante(
                        "Garantie nulle",
                        "Le lot " + lot.getNumero() + " porte une garantie de zéro franc.")));
    }

    /**
     * L'ouverture des plis vient apres la date limite de depot, et la
     * publicite laisse aux candidats le temps de repondre.
     */
    private static void calendrier(AppelOffres appel, List<Anomalie> anomalies) {
        Instant depot = appel.getDateLimiteDepot();
        Instant ouverture = appel.getOuverturePlis();

        if (depot == null) {
            anomalies.add(Anomalie.bloquante(
                    "Date limite de dépôt absente",
                    "Sans elle, aucun candidat ne sait quand déposer, et aucun contrôle "
                            + "de péremption de pièce n'a de date de référence."));
            return;
        }

        if (ouverture != null && ouverture.isBefore(depot)) {
            anomalies.add(Anomalie.bloquante(
                    "Ouverture avant la clôture",
                    "Les plis seraient ouverts avant la date limite de dépôt."));
        }

        long joursDePublicite = Duration.between(Instant.now(), depot).toDays();
        if (joursDePublicite < JOURS_MINIMUM_DE_PUBLICITE) {
            anomalies.add(Anomalie.avertissement(
                    "Délai de publicité court",
                    "Il reste %d jour(s) avant la date limite. En deçà de %d jours, un "
                            .formatted(Math.max(joursDePublicite, 0), JOURS_MINIMUM_DE_PUBLICITE)
                            + "candidat a peu de chances de réunir ses pièces, et le délai "
                            + "peut être contesté."));
        }
    }

    private static void champsIndispensables(AppelOffres appel, List<Lot> lots,
                                             List<Anomalie> anomalies) {
        List<String> manquants = new ArrayList<>();

        if (estVide(appel.getNumeroAo())) {
            manquants.add("numéro d'appel d'offres");
        }
        if (estVide(appel.getAutoriteContractante())) {
            manquants.add("autorité contractante");
        }
        if (estVide(appel.getFinancement())) {
            manquants.add("source de financement");
        }
        if (estVide(appel.getModePassation())) {
            manquants.add("mode de passation");
        }
        if (estVide(appel.getAdresseRetrait())) {
            manquants.add("adresse de retrait du dossier");
        }
        if (lots.isEmpty()) {
            manquants.add("au moins un lot");
        }

        if (!manquants.isEmpty()) {
            anomalies.add(Anomalie.bloquante(
                    "Mentions obligatoires absentes",
                    "L'avis ne peut pas être publié sans : " + String.join(", ", manquants) + "."));
        }

        lots.stream()
                .filter(lot -> lot.getDelaiMois() == null)
                .findFirst()
                .ifPresent(lot -> anomalies.add(Anomalie.avertissement(
                        "Délai d'exécution non fixé",
                        "Le lot " + lot.getNumero() + " n'annonce aucun délai maximum. "
                                + "Les offres seront incomparables sur ce point.")));
    }

    private static boolean estVide(String valeur) {
        return valeur == null || valeur.isBlank();
    }

    // ------------------------------------------------------------------ types

    public record Anomalie(String libelle, String message, boolean bloquante) {

        static Anomalie bloquante(String libelle, String message) {
            return new Anomalie(libelle, message, true);
        }

        static Anomalie avertissement(String libelle, String message) {
            return new Anomalie(libelle, message, false);
        }
    }

    public record Rapport(List<Anomalie> anomalies) {

        public List<Anomalie> bloquantes() {
            return anomalies.stream().filter(Anomalie::bloquante).toList();
        }

        public List<Anomalie> avertissements() {
            return anomalies.stream().filter(a -> !a.bloquante()).toList();
        }

        /** La seule question qui décide : le dossier peut-il être publié ? */
        public boolean publicationPossible() {
            return bloquantes().isEmpty();
        }
    }

    /**
     * La garantie telle qu'elle doit s'ecrire dans l'avis : lettres puis
     * chiffres, issus du meme nombre.
     */
    public static String garantieEnToutesLettres(long montant) {
        return "%s (%s)".formatted(
                MontantEnLettres.nombre(montant),
                MontantEnLettres.chiffres(montant));
    }
}
