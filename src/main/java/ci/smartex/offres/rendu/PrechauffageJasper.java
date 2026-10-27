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
            "rapports/renseignements.jrxml",
            "rapports/antecedents.jrxml",
            "rapports/planning.jrxml",
            "rapports/rapport_conformite.jrxml",
            // Offre financiere
            "rapports/lettre_soumission.jrxml",
            "rapports/dqe.jrxml",
            "rapports/bpu.jrxml",
            // Facturation
            "rapports/recu_paiement.jrxml");

    private final ServiceJasper jasper;
    private final AssembleurPdf assembleur;

    public PrechauffageJasper(ServiceJasper jasper, AssembleurPdf assembleur) {
        this.jasper = jasper;
        this.assembleur = assembleur;
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

        rechaufferLAssemblage();

        LOG.info("{} modele(s) de rapport compiles en {} ms. La premiere generation sera "
                        + "aussi rapide que les suivantes.",
                compiles, System.currentTimeMillis() - debut);
    }

    /**
     * Fusionne et filigrane deux pages a blanc.
     *
     * <p>PDFBox charge ses classes a la premiere fusion, ce qui coutait cinq
     * secondes au premier dossier assemble de la journee - toujours a la meme
     * personne, celle qui ouvre l'application en premier. Le resultat de cette
     * fusion est jete : seul le chargement comptait.
     */
    private void rechaufferLAssemblage() {
        try {
            byte[] page = jasper.versPdf("rapports/separateur_lot.jrxml",
                    java.util.Map.of("LOT_LIBELLE", "prechauffage"), List.of());
            assembleur.filigraner(assembleur.fusionner(List.of(page, page)));
        } catch (RuntimeException erreur) {
            // Un prechauffage qui echoue ne doit rien empecher : la premiere
            // generation sera simplement plus lente.
            LOG.warn("Prechauffage de l'assemblage impossible : {}", erreur.getMessage());
        }
    }
}
