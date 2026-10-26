package ci.smartex.offres.conformite;

import org.languagetool.JLanguageTool;
import org.languagetool.language.French;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Correcteur orthographique francais, charge une fois pour toutes.
 *
 * <p>LanguageTool charge plusieurs dizaines de megaoctets de dictionnaires au
 * premier appel : l'instance est donc unique et paresseuse, pour ne pas
 * ralentir le demarrage d'un VPS a 8 Go.
 *
 * <p>Une panne du correcteur ne doit jamais empecher un depot : c'est un
 * avertissement, pas un controle bloquant. En cas d'echec, il se tait.
 */
@Component
public class CorrecteurFrancais {

    private static final Logger LOG = LoggerFactory.getLogger(CorrecteurFrancais.class);

    /** Au-dela, le texte n'est plus une designation mais un copier-coller. */
    private static final int LONGUEUR_MAX = 2000;

    private volatile JLanguageTool outil;

    public List<String> fautes(String texte) {
        if (texte == null || texte.isBlank() || texte.length() > LONGUEUR_MAX) {
            return List.of();
        }
        try {
            return outil().check(texte).stream()
                    .map(faute -> texte.substring(faute.getFromPos(), faute.getToPos()))
                    .filter(mot -> !mot.isBlank())
                    .toList();
        } catch (Exception erreur) {
            LOG.warn("Correcteur indisponible, controle d'orthographe ignore : {}",
                    erreur.getMessage());
            return List.of();
        }
    }

    private JLanguageTool outil() {
        JLanguageTool local = outil;
        if (local == null) {
            synchronized (this) {
                local = outil;
                if (local == null) {
                    local = new JLanguageTool(new French());
                    outil = local;
                }
            }
        }
        return local;
    }
}
