package ci.smartex.offres.conformite;

import ci.smartex.offres.domain.ControleResultat;

import java.util.List;
import java.util.UUID;

/**
 * Le rapport de conformite d'un dossier, tel qu'il s'affiche a cote du PDF
 * filigrane et tel qu'il est archive avec la version validee.
 */
public record RapportConformite(UUID dossierId, List<ControleResultat> resultats) {

    public List<ControleResultat> bloquantsEnEchec() {
        return resultats.stream().filter(ControleResultat::bloque).toList();
    }

    public List<ControleResultat> avertissements() {
        return resultats.stream()
                .filter(r -> !r.isReussi())
                .filter(r -> r.getNiveau() == ControleResultat.Niveau.AVERTISSEMENT)
                .toList();
    }

    /** La seule question qui decide : le dossier peut-il partir en validation ? */
    public boolean validationPossible() {
        return bloquantsEnEchec().isEmpty();
    }

    public int nombreExecutes() {
        return resultats.size();
    }

    public int nombreBloquants() {
        return bloquantsEnEchec().size();
    }

    public int nombreAvertissements() {
        return avertissements().size();
    }
}
