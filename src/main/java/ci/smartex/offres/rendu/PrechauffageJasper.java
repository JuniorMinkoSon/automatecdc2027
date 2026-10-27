package ci.smartex.offres.rendu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Compile tous les modeles au demarrage, pour que la premiere generation soit
 * aussi rapide que les suivantes.
 *
 * <p>La compilation d'un .jrxml coute plusieurs centaines de millisecondes.
 * Payee au premier clic, elle ferait passer une generation de deux secondes a
 * dix, et c'est le premier utilisateur de la journee qui en ferait les frais -
 * celui-la meme qui travaille sous echeance. Payee ici, elle ne se voit jamais.
 *
 * <p>Le prechauffage est asynchrone : l'application repond pendant qu'il
 * tourne. Un modele qui ne compile pas est signale sans empecher le demarrage,
 * puisque les autres restent utilisables - mais il est signale, et non tu.
 */
@Component
@Order(100)
public class PrechauffageJasper implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(PrechauffageJasper.class);

    /**
     * Les modeles livres avec l'application. Les modeles Word televerses par
     * les entreprises n'ont pas besoin de compilation.
     */
    private static final List<String> MODELES = List.of(
            // Communs aux trois documents
            "rapports/page_garde.jrxml",
            "rapports/sommaire.jrxml",
            "rapports/separateur_lot.jrxml",
            // Dossier d'appel d'offres
            "rapports/page_garde_dao.jrxml",
            "rapports/avis_appel_offres.jrxml",
            "rapports/section_dao.jrxml",
            // Offre technique
            "rapports/lettre_type.jrxml",
            "rapports/tableau_moyens.jrxml",
            "rapports/rapport_conformite.jrxml",
            // Offre financiere
            "rapports/lettre_soumission.jrxml",
            "rapports/dqe.jrxml",
            "rapports/bpu.jrxml");

    private final ServiceJasper jasper;

    public PrechauffageJasper(ServiceJasper jasper) {
        this.jasper = jasper;
    }

    @Override
    @Async
    public void run(ApplicationArguments arguments) {
        long debut = System.currentTimeMillis();
        int compiles = 0;

        for (String modele : MODELES) {
            try {
                jasper.precharger(modele);
                compiles++;
            } catch (RuntimeException erreur) {
                // Un modele casse ne doit pas empecher les autres de servir,
                // mais il ne doit pas passer inapercu non plus : il echouera
                // de toute facon au premier clic, autant le savoir maintenant.
                LOG.error("Le modele {} ne compile pas. Les documents qui en dependent "
                        + "echoueront.", modele, erreur);
            }
        }

        LOG.info("{} modele(s) de rapport compiles en {} ms. La premiere generation sera "
                        + "aussi rapide que les suivantes.",
                compiles, System.currentTimeMillis() - debut);
    }
}
