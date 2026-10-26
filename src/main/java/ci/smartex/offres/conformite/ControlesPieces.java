package ci.smartex.offres.conformite;

import ci.smartex.offres.domain.ControleResultat.Niveau;
import ci.smartex.offres.domain.PieceAdmin;
import ci.smartex.offres.util.MontantEnLettres;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Les pieces du coffre, jugees a la date limite de depot et non au jour du
 * controle : une attestation qui expire entre les deux est deja perimee pour
 * la COJO.
 */
public final class ControlesPieces {

    private ControlesPieces() {
    }

    /** Pieces exigees de tout soumissionnaire, quel que soit le marche. */
    private static final List<PieceAdmin.Type> PIECES_DE_BASE = List.of(
            PieceAdmin.Type.RCCM,
            PieceAdmin.Type.DFE,
            PieceAdmin.Type.ATTESTATION_PME,
            PieceAdmin.Type.ATTESTATION_BANCAIRE);

    @Component
    public static class GarantieOffre implements Controle {

        @Override
        public String code() {
            return "GARANTIE_OFFRE";
        }

        @Override
        public String libelle() {
            return "Garantie d'offre";
        }

        @Override
        public Niveau niveau() {
            return Niveau.BLOQUANT;
        }

        @Override
        public Verdict verifier(DossierAControler d) {
            var cautionnement = d.pieceDeType(PieceAdmin.Type.CAUTIONNEMENT);
            String lien = "/coffre";

            if (cautionnement.isEmpty()) {
                return Verdict.echec(
                        "Le cautionnement provisoire est absent ou perime a la date limite. "
                                + "C'est une piece eliminatoire.", lien);
            }

            Long exige = d.lot().map(lot -> lot.getMontantGarantie()).orElse(null);
            Long porte = cautionnement.get().getMontant();

            // Si l'OCR n'a pas su lire le montant, on ne bloque pas sur un
            // chiffre invente : la presence suffit, et l'avertissement
            // MONTANT_GARANTIE demande au validateur de verifier de ses yeux.
            if (exige == null || porte == null) {
                return Verdict.conforme();
            }

            return Verdict.exiger(porte >= exige,
                    "La caution porte " + MontantEnLettres.chiffres(porte)
                            + " alors que le DAO en exige " + MontantEnLettres.chiffres(exige)
                            + " pour ce lot.", lien);
        }
    }

    @Component
    public static class QuitusArcop implements Controle {

        @Override
        public String code() {
            return "QUITUS_ARCOP";
        }

        @Override
        public String libelle() {
            return "Quitus ARCOP";
        }

        @Override
        public Niveau niveau() {
            return Niveau.BLOQUANT;
        }

        @Override
        public Verdict verifier(DossierAControler d) {
            var quitus = d.pieces().stream()
                    .filter(p -> p.getType() == PieceAdmin.Type.QUITUS_ARCOP)
                    .findFirst();

            if (quitus.isEmpty()) {
                return Verdict.echec(
                        "Le quitus ARCOP est absent du coffre. C'est une piece eliminatoire.",
                        "/coffre");
            }
            if (!quitus.get().isConfirmee()) {
                return Verdict.echec(
                        "Le quitus ARCOP attend votre confirmation de lecture.",
                        "/coffre");
            }
            if (quitus.get().expireAvant(d.dateReference())) {
                return Verdict.echec(
                        "Le quitus ARCOP expire le " + quitus.get().getDateExpiration()
                                + ", avant la date limite de depot. Remplacez-le.",
                        "/coffre");
            }
            return Verdict.conforme();
        }
    }

    @Component
    public static class PiecesAdministratives implements Controle {

        @Override
        public String code() {
            return "PIECES_ADMINISTRATIVES";
        }

        @Override
        public String libelle() {
            return "Pieces administratives";
        }

        @Override
        public Niveau niveau() {
            return Niveau.BLOQUANT;
        }

        @Override
        public Verdict verifier(DossierAControler d) {
            List<String> manquantes = PIECES_DE_BASE.stream()
                    .filter(type -> d.pieceDeType(type).isEmpty())
                    .map(ControlesPieces::nommer)
                    .collect(Collectors.toList());

            if (manquantes.isEmpty()) {
                return Verdict.conforme();
            }
            return Verdict.echec(
                    "Absentes ou perimees a la date limite : " + String.join(", ", manquantes)
                            + ".", "/coffre");
        }
    }

    /**
     * Ne se declenche que lorsque le montant de la caution n'a pas pu etre lu :
     * la machine passe alors la main plutot que de laisser croire qu'elle a
     * verifie.
     */
    @Component
    public static class MontantGarantieARelire implements Controle {

        @Override
        public String code() {
            return "MONTANT_GARANTIE";
        }

        @Override
        public String libelle() {
            return "Montant de la garantie";
        }

        @Override
        public Niveau niveau() {
            return Niveau.AVERTISSEMENT;
        }

        @Override
        public boolean sApplique(DossierAControler d) {
            boolean lotChiffre = d.lot().map(lot -> lot.getMontantGarantie() != null).orElse(false);
            boolean montantIllisible = d.pieceDeType(PieceAdmin.Type.CAUTIONNEMENT)
                    .map(piece -> piece.getMontant() == null)
                    .orElse(false);
            return lotChiffre && montantIllisible;
        }

        @Override
        public Verdict verifier(DossierAControler d) {
            long exige = d.lot().orElseThrow().getMontantGarantie();
            return Verdict.echec(
                    "Le montant de la caution n'a pas pu etre lu. Verifiez qu'elle porte au "
                            + "moins " + MontantEnLettres.chiffres(exige) + ".",
                    "/coffre");
        }
    }

    private static String nommer(PieceAdmin.Type type) {
        return switch (type) {
            case RCCM -> "registre de commerce (RCCM)";
            case DFE -> "declaration fiscale d'existence";
            case ATTESTATION_PME -> "attestation PME";
            case QUITUS_ARCOP -> "quitus ARCOP";
            case ATTESTATION_BANCAIRE -> "attestation bancaire";
            case ATTESTATION_FISCALE -> "attestation fiscale";
            case CNPS -> "attestation CNPS";
            case CAUTIONNEMENT -> "cautionnement provisoire";
            case AUTRE -> "piece complementaire";
        };
    }
}
