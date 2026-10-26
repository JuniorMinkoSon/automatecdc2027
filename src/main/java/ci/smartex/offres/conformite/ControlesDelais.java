package ci.smartex.offres.conformite;

import ci.smartex.offres.domain.ControleResultat.Niveau;
import org.springframework.stereotype.Component;

/**
 * Les trois engagements de duree du DAO de la Bagoue : delai d'execution
 * (IC 13.2), validite de l'offre (IC 19.1) et validite de la garantie
 * (IC 20, validite de l'offre augmentee de trente jours).
 *
 * <p>Tous trois sont bloquants parce qu'ils se verifient par une comparaison de
 * nombres : la plateforme ne peut pas se tromper, donc elle ne demande pas
 * l'avis du validateur.
 */
public final class ControlesDelais {

    private ControlesDelais() {
    }

    @Component
    public static class DelaiExecution implements Controle {

        @Override
        public String code() {
            return "DELAI_EXECUTION";
        }

        @Override
        public String libelle() {
            return "Delai d'execution";
        }

        @Override
        public Niveau niveau() {
            return Niveau.BLOQUANT;
        }

        @Override
        public boolean sApplique(DossierAControler d) {
            return d.lot().map(lot -> lot.getDelaiMois() != null).orElse(false);
        }

        @Override
        public Verdict verifier(DossierAControler d) {
            Integer engage = d.dossier().getDelaiExecutionMois();
            int maximum = d.lot().orElseThrow().getDelaiMois();

            if (engage == null) {
                return Verdict.echec(
                        "Le delai d'execution n'est pas renseigne ; le DAO en impose "
                                + maximum + " au maximum.",
                        "/dossiers/" + d.dossier().getId() + "/engagements");
            }
            return Verdict.exiger(engage <= maximum,
                    "Le delai engage (" + engage + " mois) depasse le maximum du DAO ("
                            + maximum + " mois).",
                    "/dossiers/" + d.dossier().getId() + "/engagements");
        }
    }

    @Component
    public static class ValiditeOffre implements Controle {

        @Override
        public String code() {
            return "VALIDITE_OFFRE";
        }

        @Override
        public String libelle() {
            return "Validite de l'offre";
        }

        @Override
        public Niveau niveau() {
            return Niveau.BLOQUANT;
        }

        @Override
        public boolean sApplique(DossierAControler d) {
            return d.appelOffres().getValiditeOffreJours() != null;
        }

        @Override
        public Verdict verifier(DossierAControler d) {
            Integer engagee = d.dossier().getValiditeOffreJours();
            int exigee = d.appelOffres().getValiditeOffreJours();

            if (engagee == null) {
                return Verdict.echec(
                        "La validite de l'offre n'est pas renseignee ; le DAO exige "
                                + exigee + " jours.",
                        "/dossiers/" + d.dossier().getId() + "/engagements");
            }
            // Une validite plus longue que demandee n'est pas un avantage : le
            // DAO fixe une duree, pas un minimum, et l'ecart se lit comme une
            // offre non conforme.
            return Verdict.exiger(engagee == exigee,
                    "La validite engagee (" + engagee + " jours) ne correspond pas a celle "
                            + "du DAO (" + exigee + " jours).",
                    "/dossiers/" + d.dossier().getId() + "/engagements");
        }
    }

    @Component
    public static class ValiditeGarantie implements Controle {

        /** Le DAO exige la validite de l'offre augmentee de trente jours. */
        private static final int JOURS_SUPPLEMENTAIRES = 30;

        @Override
        public String code() {
            return "VALIDITE_GARANTIE";
        }

        @Override
        public String libelle() {
            return "Validite de la garantie";
        }

        @Override
        public Niveau niveau() {
            return Niveau.BLOQUANT;
        }

        @Override
        public boolean sApplique(DossierAControler d) {
            return d.appelOffres().getValiditeOffreJours() != null;
        }

        @Override
        public Verdict verifier(DossierAControler d) {
            int attendue = d.appelOffres().getValiditeOffreJours() + JOURS_SUPPLEMENTAIRES;
            Integer engagee = d.dossier().getValiditeGarantieJours();

            if (engagee == null) {
                return Verdict.echec(
                        "La validite de la garantie n'est pas renseignee ; elle doit etre de "
                                + attendue + " jours.",
                        "/dossiers/" + d.dossier().getId() + "/engagements");
            }
            return Verdict.exiger(engagee == attendue,
                    "La validite de la garantie (" + engagee + " jours) doit etre celle de "
                            + "l'offre augmentee de " + JOURS_SUPPLEMENTAIRES + " jours, soit "
                            + attendue + " jours.",
                    "/dossiers/" + d.dossier().getId() + "/engagements");
        }
    }
}
