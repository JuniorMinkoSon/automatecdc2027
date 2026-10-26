package ci.smartex.offres.conformite;

import ci.smartex.offres.domain.ControleResultat.Niveau;
import ci.smartex.offres.domain.LigneDqe;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Ce qui se lit plutot que ce qui se calcule : regime de prix, champs
 * d'identite qui alimentent toutes les pieces, et orthographe des textes
 * saisis librement.
 *
 * <p>Tous avertissants. Un DAO qui exige des prix TTC alors que la TVA n'est
 * pas prise en compte pose une vraie question ; la plateforme la pose au
 * soumissionnaire au lieu d'y repondre a sa place.
 */
public final class ControlesRedaction {

    private ControlesRedaction() {
    }

    @Component
    public static class RegimeDePrix implements Controle {

        @Override
        public String code() {
            return "REGIME_PRIX";
        }

        @Override
        public String libelle() {
            return "Regime de prix";
        }

        @Override
        public Niveau niveau() {
            return Niveau.AVERTISSEMENT;
        }

        @Override
        public boolean sApplique(DossierAControler d) {
            String regime = d.appelOffres().getRegimePrix();
            return regime != null && regime.toUpperCase().contains("TTC");
        }

        @Override
        public Verdict verifier(DossierAControler d) {
            Boolean tva = d.appelOffres().getTvaApplicable();
            String lien = "/appels-offres/" + d.appelOffres().getId();

            if (tva == null) {
                return Verdict.echec(
                        "Le DAO exige des prix TTC mais le sort de la TVA n'est pas tranche. "
                                + "Appliquez-la, ou justifiez l'exoneration dans le dossier.",
                        lien);
            }
            if (!tva) {
                return Verdict.echec(
                        "Les prix sont annonces TTC avec une TVA non prise en compte. "
                                + "La mention du regime d'exoneration doit figurer au dossier.",
                        lien);
            }
            return Verdict.conforme();
        }
    }

    /**
     * Les champs qui alimentent toutes les pieces a la fois. Dans la plateforme
     * ils ont une source unique, donc ils ne peuvent plus diverger d'une piece
     * a l'autre comme dans les offres deposees jusqu'ici ; ce qui peut encore
     * arriver, c'est qu'ils soient vides, et impriment un blanc partout.
     */
    @Component
    public static class IdentiteComplete implements Controle {

        @Override
        public String code() {
            return "COHERENCE_IDENTITE";
        }

        @Override
        public String libelle() {
            return "Coherence d'identite";
        }

        @Override
        public Niveau niveau() {
            return Niveau.AVERTISSEMENT;
        }

        @Override
        public Verdict verifier(DossierAControler d) {
            List<String> vides = new ArrayList<>();

            if (estVide(d.entreprise().getRaisonSociale())) {
                vides.add("raison sociale");
            }
            if (estVide(d.entreprise().getRccm())) {
                vides.add("numero RCCM");
            }
            if (estVide(d.entreprise().getCompteContribuable())) {
                vides.add("compte contribuable");
            }
            if (estVide(d.appelOffres().getNumeroAo())) {
                vides.add("numero d'appel d'offres");
            }
            if (estVide(d.appelOffres().getIntitule())) {
                vides.add("intitule du marche");
            }
            if (estVide(d.appelOffres().getAutoriteContractante())) {
                vides.add("autorite contractante");
            }

            if (vides.isEmpty()) {
                return Verdict.conforme();
            }
            return Verdict.echec(
                    "Ces champs apparaitront en blanc sur toutes les pieces : "
                            + String.join(", ", vides) + ".",
                    "/entreprise");
        }

        private boolean estVide(String valeur) {
            return valeur == null || valeur.isBlank();
        }
    }

    /**
     * Correcteur francais sur les seuls textes qu'un humain a tapes.
     *
     * <p>Les designations issues du DAO ne sont pas relues : elles doivent
     * rester telles que l'autorite contractante les a ecrites, fautes
     * comprises, sous peine de ne plus correspondre au bordereau.
     */
    @Component
    public static class Orthographe implements Controle {

        private final CorrecteurFrancais correcteur;

        public Orthographe(CorrecteurFrancais correcteur) {
            this.correcteur = correcteur;
        }

        @Override
        public String code() {
            return "ORTHOGRAPHE";
        }

        @Override
        public String libelle() {
            return "Orthographe";
        }

        @Override
        public Niveau niveau() {
            return Niveau.AVERTISSEMENT;
        }

        @Override
        public boolean sApplique(DossierAControler d) {
            return !textesLibres(d).isEmpty();
        }

        @Override
        public Verdict verifier(DossierAControler d) {
            List<String> fautes = new ArrayList<>();
            for (String texte : textesLibres(d)) {
                fautes.addAll(correcteur.fautes(texte));
            }

            if (fautes.isEmpty()) {
                return Verdict.conforme();
            }
            // Au-dela de cinq, la liste cesse d'aider : le validateur ira voir.
            List<String> extrait = fautes.stream().distinct().limit(5).toList();
            return Verdict.echec(
                    fautes.size() + " faute(s) possible(s) dans les textes saisis : "
                            + String.join(", ", extrait) + ".",
                    "/dossiers/" + d.dossier().getId() + "/prix");
        }

        private List<String> textesLibres(DossierAControler d) {
            return d.lignes().stream()
                    .filter(ligne -> !ligne.isIssueDuDao())
                    .map(LigneDqe::getDesignation)
                    .filter(texte -> texte != null && !texte.isBlank())
                    .toList();
        }
    }
}
