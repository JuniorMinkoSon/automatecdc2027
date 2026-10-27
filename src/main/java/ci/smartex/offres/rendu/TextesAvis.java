package ci.smartex.offres.rendu;

import ci.smartex.offres.domain.AppelOffres;
import ci.smartex.offres.domain.Lot;
import ci.smartex.offres.util.MontantEnLettres;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Les clauses numerotees d'un avis d'appel d'offres.
 *
 * <p>Ce sont des textes reglementaires : ils engagent l'autorite contractante
 * et fondent les recours. Les tenir sous Git les met sous revue et sous
 * historique, ce qu'un editeur de gabarit ne permet pas.
 *
 * <p>Aucune valeur n'y est ecrite en dur. Les durees, les montants, les
 * adresses et les dates viennent des memes champs que le reglement : c'est ce
 * qui empeche l'avis de le contredire. L'avis de reference annoncait « cinq
 * cent cinquante mille (850 000) francs CFA » - deux montants dans une seule
 * phrase - et une validite de soixante jours quand le reglement en demandait
 * cent vingt. Ici les deux ecritures d'un montant sortent du meme entier, et
 * la validite n'existe qu'une fois.
 */
public final class TextesAvis {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");
    private static final DateTimeFormatter JOUR_ET_HEURE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH'h'mm", Locale.FRENCH);

    private TextesAvis() {
    }

    public static String clauses(AppelOffres appel, List<Lot> lots) {
        StringBuilder texte = new StringBuilder();
        int numero = 1;

        texte.append(clause(numero++, origineDesFonds(appel)));
        texte.append(clause(numero++, allotissement(lots)));
        texte.append(clause(numero++, modeDePassation(appel)));
        texte.append(clause(numero++, renseignements(appel)));
        texte.append(clause(numero++, qualifications(appel)));
        texte.append(clause(numero++, retraitDuDossier(appel)));
        texte.append(clause(numero++, remiseDesOffres(appel)));
        texte.append(clause(numero++, ouvertureDesPlis(appel)));
        texte.append(clause(numero++, cautionnement(lots)));
        texte.append(clause(numero++, validiteDesOffres(appel)));
        texte.append(clause(numero++, attribution(appel)));
        texte.append(clause(numero++, formalites(appel)));
        texte.append(clause(numero, droitApplicable(appel)));

        return texte.toString();
    }

    // ------------------------------------------------------------------ clauses

    private static String origineDesFonds(AppelOffres appel) {
        String financement = ouACompleter(appel.getFinancement(), "la source de financement");
        return ("L'autorité contractante %s dispose de fonds au titre de %s, et a "
                + "l'intention d'utiliser une partie de ces fonds pour effectuer les "
                + "paiements au titre du marché issu du présent appel d'offres, relatif à : %s.")
                .formatted(ouACompleter(appel.getAutoriteContractante(), "l'autorité contractante"),
                        financement, appel.getIntitule());
    }

    private static String allotissement(List<Lot> lots) {
        if (lots.isEmpty()) {
            return "[À COMPLÉTER : aucun lot n'est défini. L'avis ne peut pas être publié.]";
        }
        if (lots.size() == 1) {
            return "Les travaux objet du présent appel d'offres ne sont pas allotis. "
                    + "Le détail figure au tableau ci-dessus.";
        }
        return "Les travaux objet du présent appel d'offres sont composés de %s (%d) lots, "
                .formatted(MontantEnLettres.nombre(lots.size()), lots.size())
                + "dont le détail figure au tableau ci-dessus.";
    }

    private static String modeDePassation(AppelOffres appel) {
        return "La passation du marché sera conduite par %s, ouvert à tous les candidats "
                .formatted(ouACompleter(appel.getModePassation(), "le mode de passation").toLowerCase())
                + "éligibles au sens du Code des marchés publics.";
    }

    private static String renseignements(AppelOffres appel) {
        String adresse = ouACompleter(appel.getAdresseRetrait(), "l'adresse de retrait");
        String contacts = appel.getContacts() == null || appel.getContacts().isBlank()
                ? "" : " Contacts : " + appel.getContacts() + ".";
        return "Les candidats intéressés peuvent obtenir des informations et prendre "
                + "connaissance des documents d'appel d'offres à l'adresse suivante : "
                + adresse + "." + contacts;
    }

    private static String qualifications(AppelOffres appel) {
        String exigences = appel.getQualificationsExigees();
        if (exigences == null || exigences.isBlank()) {
            return "[À COMPLÉTER : les exigences de qualification ne sont pas renseignées. "
                    + "Sans elles, aucune offre ne peut être écartée pour insuffisance de "
                    + "capacité sans risque de recours.]";
        }
        return "Les exigences en matière de qualification sont : " + exigences
                + " Le détail figure à la section relative aux critères d'évaluation "
                + "et de qualification.";
    }

    private static String retraitDuDossier(AppelOffres appel) {
        if (appel.getPrixDossier() == null) {
            return "Le dossier d'appel d'offres peut être consulté gratuitement à l'adresse "
                    + "indiquée ci-dessus.";
        }
        String moyen = appel.getMoyenPaiementDossier() == null
                || appel.getMoyenPaiementDossier().isBlank()
                ? "" : " par " + appel.getMoyenPaiementDossier();
        return ("Les candidats peuvent consulter gratuitement ou retirer le dossier d'appel "
                + "d'offres contre un paiement forfaitaire%s, non remboursable, de %s (%s), "
                + "à l'adresse indiquée ci-dessus.")
                .formatted(moyen,
                        MontantEnLettres.nombre(appel.getPrixDossier()),
                        MontantEnLettres.chiffres(appel.getPrixDossier()));
    }

    private static String remiseDesOffres(AppelOffres appel) {
        if (appel.getDateLimiteDepot() == null) {
            return "[À COMPLÉTER : la date limite de dépôt n'est pas fixée. C'est elle qui "
                    + "sert de référence à toutes les pièces exigées.]";
        }
        String ou = appel.getPlateformeDepot() == null || appel.getPlateformeDepot().isBlank()
                ? "à l'adresse indiquée ci-dessus"
                : "sur " + appel.getPlateformeDepot()
                  + (appel.getUrlDepot() == null || appel.getUrlDepot().isBlank()
                     ? "" : ", accessible à l'adresse " + appel.getUrlDepot());
        return "Les offres devront être soumises %s, au plus tard le %s. Les offres remises "
                .formatted(ou, horodatage(appel.getDateLimiteDepot()))
                + "en retard ne seront pas acceptées.";
    }

    private static String ouvertureDesPlis(AppelOffres appel) {
        if (appel.getOuverturePlis() == null) {
            return "[À COMPLÉTER : la date d'ouverture des plis n'est pas fixée.]";
        }
        String lieu = appel.getLieuOuverture() == null || appel.getLieuOuverture().isBlank()
                ? "" : ", à " + appel.getLieuOuverture();
        return "L'ouverture des plis sera effectuée par la Commission d'ouverture des plis "
                + "et de jugement des offres, en séance publique, le "
                + horodatage(appel.getOuverturePlis()) + lieu + ".";
    }

    /**
     * Le montant du cautionnement, en lettres puis en chiffres, tires du meme
     * entier. C'est ici que l'avis de reference se contredisait.
     */
    private static String cautionnement(List<Lot> lots) {
        List<Long> montants = lots.stream()
                .map(Lot::getMontantGarantie)
                .filter(montant -> montant != null)
                .distinct()
                .toList();

        if (montants.isEmpty()) {
            return "[À COMPLÉTER : aucun montant de cautionnement provisoire n'est fixé.]";
        }
        if (montants.size() == 1) {
            long montant = montants.get(0);
            return ("Les offres doivent comprendre un cautionnement provisoire établi par une "
                    + "banque, un organisme financier ou un tiers agréé par le ministre chargé "
                    + "des finances, d'un montant de %s (%s) par lot.")
                    .formatted(MontantEnLettres.nombre(montant),
                            MontantEnLettres.chiffres(montant));
        }

        StringBuilder detail = new StringBuilder(
                "Les offres doivent comprendre un cautionnement provisoire établi par une "
                + "banque, un organisme financier ou un tiers agréé par le ministre chargé "
                + "des finances, dont le montant varie selon le lot :");
        lots.stream()
                .filter(lot -> lot.getMontantGarantie() != null)
                .forEach(lot -> detail.append("\n      - lot %d : %s (%s) ;".formatted(
                        lot.getNumero(),
                        MontantEnLettres.nombre(lot.getMontantGarantie()),
                        MontantEnLettres.chiffres(lot.getMontantGarantie()))));
        return detail.toString();
    }

    /**
     * Une seule duree, lue une seule fois. Le controle de publication refuse
     * que la duree annoncee et celle du reglement different ; ce texte, lui,
     * ne peut pas les faire diverger puisqu'il n'en connait qu'une.
     */
    private static String validiteDesOffres(AppelOffres appel) {
        Integer jours = appel.getValiditeAnnonceeJours() != null
                ? appel.getValiditeAnnonceeJours()
                : appel.getValiditeOffreJours();

        if (jours == null) {
            return "[À COMPLÉTER : la durée de validité des offres n'est pas fixée.]";
        }
        return "Les soumissionnaires resteront engagés par leur offre pendant un délai de "
                + "%s (%d) jours à compter de la date limite de dépôt des offres."
                        .formatted(MontantEnLettres.nombre(jours), jours);
    }

    private static String attribution(AppelOffres appel) {
        return "Dès la validation de la décision d'attribution, l'autorité contractante "
                + "publiera la décision au Bulletin officiel des marchés publics et par voie "
                + "d'affichage dans ses locaux, et tiendra le rapport d'analyse à la "
                + "disposition des soumissionnaires.";
    }

    private static String formalites(AppelOffres appel) {
        String taux = appel.getRedevanceArcop() == null
                ? "0,5 %"
                : appel.getRedevanceArcop().stripTrailingZeros().toPlainString()
                        .replace('.', ',') + " %";
        return "Le ou les marchés issus du présent appel d'offres seront soumis aux "
                + "formalités de timbre et d'enregistrement, ainsi qu'à la redevance de "
                + "régulation de " + taux + " du montant hors taxes, à reverser à l'Autorité "
                + "de régulation de la commande publique, aux frais du titulaire.";
    }

    private static String droitApplicable(AppelOffres appel) {
        String reference = appel.getReferenceCodeMarches() == null
                || appel.getReferenceCodeMarches().isBlank()
                ? "le Code des marchés publics en vigueur"
                : appel.getReferenceCodeMarches();
        return "Le présent appel d'offres est soumis aux lois et règlements en vigueur en "
                + "Côte d'Ivoire, et notamment à " + reference + ".";
    }

    // ------------------------------------------------------------------ outils

    private static String clause(int numero, String texte) {
        return "%d.   %s%n%n".formatted(numero, texte);
    }

    /**
     * Un champ absent se voit, et dit ce qui manque. Laisser un blanc le
     * ferait passer a la publication.
     */
    private static String ouACompleter(String valeur, String quoi) {
        return valeur == null || valeur.isBlank()
                ? "[à compléter : " + quoi + "]"
                : valeur;
    }

    private static String horodatage(java.time.Instant instant) {
        ZonedDateTime local = instant.atZone(ABIDJAN);
        return local.format(JOUR_ET_HEURE) + " (temps universel)";
    }
}
