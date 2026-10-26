package ci.smartex.offres;

import ci.smartex.offres.config.ProprietesApp;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Plateforme de generation automatique des offres.
 *
 * <p>Une seule application : elle sert les pages, porte le controle des droits,
 * enchaine les taches de fond et rend les PDF. JasperReports etant une
 * bibliotheque Java, le rendu vit ici plutot que dans un service a part ; il
 * n'y a donc ni serveur Jasper a securiser, ni deuxieme base de code.
 */
@SpringBootApplication
@EnableConfigurationProperties(ProprietesApp.class)
@EnableAsync
public class ApplicationOffres {

    public static void main(String[] args) {
        SpringApplication.run(ApplicationOffres.class, args);
    }
}
