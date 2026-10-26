package ci.smartex.offres.conformite;

import ci.smartex.offres.domain.ControleResultat.Niveau;
import ci.smartex.offres.domain.LigneDqe;
import ci.smartex.offres.util.MontantEnLettres;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Le chiffrage : arithmetique du DQE, montant en lettres, lots au meme prix.
 *
 * <p>La COJO corrige elle-meme les erreurs arithmetiques (IC 31.3), et une
 * correction change le classement. L'arithmetique est donc garantie deux fois :
 * la base calcule chaque montant comme colonne generee, et ce controle
 * recalcule la somme pour verifier que rien n'a ete ecrit a cote.
 */
public final class ControlesChiffrage {

    private ControlesChiffrage() {
    }

    @Component
    public static class ArithmetiqueDqe implements Controle {

        @Override
        public String code() {
            return "ARITHMETIQUE_DQE";
        }

        @Override
        public String libelle() {
            return "Arithmetique du DQE";
        }

        @Override
        public Niveau niveau() {
            return Niveau.BLOQUANT;
        }

        @Override
        public Verdict verifier(DossierAControler d) {
            String lien = "/dossiers/" + d.dossier().getId() + "/prix";

            if (d.lignes().isEmpty()) {
                return Verdict.echec("Le DQE ne contient aucune ligne.", lien);
            }

            long manquants = d.lignes().stream().filter(LigneDqe::prixManquant).count();
            if (manquants > 0) {
                return Verdict.echec(manquants + " prix unitaire(s) non saisi(s) dans le DQE.",
                        lien);
            }

            // Recalcul independant de la colonne generee : si les deux
            // divergent, c'est que quelque chose a ecrit un montant a la main.
            for (LigneDqe ligne : d.lignes()) {
                long attendu = ligne.getQuantite()
                        .multiply(BigDecimal.valueOf(ligne.getPrixUnitaire()))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValueExact();
                if (attendu != ligne.montantOuZero()) {
                    return Verdict.echec(
                            "Ligne " + ligne.getNumeroPrix() + " : le montant enregistre ("
                                    + ligne.montantOuZero() + ") ne correspond pas a "
                                    + "quantite x prix unitaire (" + attendu + ").", lien);
                }
            }

            List<String> designationsVides = d.lignes().stream()
                    .filter(LigneDqe::designationManquante)
                    .map(LigneDqe::getNumeroPrix)
                    .toList();
            if (!designationsVides.isEmpty()) {
                return Verdict.echec("Ligne(s) sans designation : "
                        + String.join(", ", designationsVides) + ".", lien);
            }

            return Verdict.conforme();
        }
    }

    @Component
    public static class MontantEnLettresCoherent implements Controle {

        @Override
        public String code() {
            return "MONTANT_EN_LETTRES";
        }

        @Override
        public String libelle() {
            return "Montant en lettres";
        }

        @Override
        public Niveau niveau() {
            return Niveau.BLOQUANT;
        }

        @Override
        public boolean sApplique(DossierAControler d) {
            return d.dqeComplet();
        }

        @Override
        public Verdict verifier(DossierAControler d) {
            long recalcule = d.totalDqe();
            Long fige = d.dossier().getMontantSoumission();
            String lien = "/dossiers/" + d.dossier().getId() + "/prix";

            // Les chiffres et les lettres de la lettre de soumission sortent
            // tous deux du montant fige sur le dossier. Ce qui peut diverger,
            // c'est ce montant et la somme reelle des lignes : un prix modifie
            // apres coup, une regeneration qui n'a pas eu lieu. C'est cela que
            // le controle attrape.
            if (fige == null) {
                return Verdict.echec(
                        "Le montant de la soumission n'a pas encore ete fige. Regenerez le "
                                + "brouillon apres avoir saisi les prix.", lien);
            }

            return Verdict.exiger(fige == recalcule,
                    "La lettre de soumission porte " + MontantEnLettres.chiffres(fige)
                            + " alors que le DQE totalise aujourd'hui "
                            + MontantEnLettres.chiffres(recalcule)
                            + ". Regenerez le brouillon.", lien);
        }
    }

    /**
     * Un numero de prix en double dans une section, c'est une ligne dupliquee
     * par copier-coller. C'est de la que viennent les erreurs d'unite du genre
     * « beton de proprete en m2 au lieu de m3 » : on copie la ligne voisine et
     * on oublie de changer ce qu'il fallait.
     *
     * <p>Les sous-totaux, eux, ne sont pas controles : Jasper les calcule a
     * partir des memes lignes que le total, ils ne peuvent pas en differer.
     */
    @Component
    public static class NumerosDePrixUniques implements Controle {

        @Override
        public String code() {
            return "NUMEROS_PRIX";
        }

        @Override
        public String libelle() {
            return "Numeros de prix";
        }

        @Override
        public Niveau niveau() {
            return Niveau.BLOQUANT;
        }

        @Override
        public boolean sApplique(DossierAControler d) {
            return !d.lignes().isEmpty();
        }

        @Override
        public Verdict verifier(DossierAControler d) {
            Map<String, Long> occurrences = d.lignes().stream()
                    .collect(Collectors.groupingBy(
                            ligne -> ligne.getSection() + " / " + ligne.getNumeroPrix(),
                            Collectors.counting()));

            List<String> doublons = occurrences.entrySet().stream()
                    .filter(entree -> entree.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();

            return Verdict.exiger(doublons.isEmpty(),
                    "Numero(s) de prix en double : " + String.join(", ", doublons)
                            + ". Une ligne a sans doute ete copiee.",
                    "/dossiers/" + d.dossier().getId() + "/prix");
        }
    }

    /**
     * Dans l'offre deposee, les lots 1 et 2 portaient le meme montant au franc
     * pres. Ce n'est pas interdit, mais c'est assez improbable pour meriter
     * qu'on redemande avant de deposer.
     */
    @Component
    public static class LotsAuMemeMontant implements Controle {

        @Override
        public String code() {
            return "LOTS_IDENTIQUES";
        }

        @Override
        public String libelle() {
            return "Lots au meme montant";
        }

        @Override
        public Niveau niveau() {
            return Niveau.AVERTISSEMENT;
        }

        @Override
        public boolean sApplique(DossierAControler d) {
            return d.dqeComplet() && !d.totauxDesAutresLots().isEmpty();
        }

        @Override
        public Verdict verifier(DossierAControler d) {
            long total = d.totalDqe();
            boolean doublon = d.totauxDesAutresLots().stream().anyMatch(autre -> autre == total);

            return Verdict.exiger(!doublon,
                    "Ce lot porte exactement le meme montant qu'un autre lot du meme appel ("
                            + MontantEnLettres.chiffres(total) + "). Verifiez les quantites.",
                    "/dossiers/" + d.dossier().getId() + "/prix");
        }
    }
}
