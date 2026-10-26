package ci.smartex.offres.rendu;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.export.SimpleDocxReportConfiguration;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimplePdfReportConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compile et execute les modeles Jasper, dans l'application meme.
 *
 * <p>JasperReports est une bibliotheque Java : il n'y a donc ni serveur Jasper
 * a installer, ni port supplementaire a fermer, ni seconde base de code. Les
 * modeles sont versionnes dans Git et charges depuis le jar : un client ne
 * peut pas televerser le sien, donc personne ne peut glisser une clause dans
 * un formulaire officiel.
 *
 * <p>La compilation d'un .jrxml coute plusieurs centaines de millisecondes :
 * les modeles compiles sont donc gardes en memoire. Ils ne changent qu'au
 * deploiement, le cache n'a pas besoin d'etre invalide.
 */
@Service
public class ServiceJasper {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceJasper.class);

    private final Map<String, JasperReport> compiles = new ConcurrentHashMap<>();

    /** Rend un modele en PDF, le seul livrable officiel. */
    public byte[] versPdf(String cheminJrxml,
                          Map<String, Object> parametres,
                          Collection<?> lignes) {
        JasperPrint impression = remplir(cheminJrxml, parametres, lignes);

        try {
            var sortie = new ByteArrayOutputStream();
            JRPdfExporter exporteur = new JRPdfExporter();
            exporteur.setExporterInput(new SimpleExporterInput(impression));
            exporteur.setExporterOutput(new SimpleOutputStreamExporterOutput(sortie));

            var configuration = new SimplePdfReportConfiguration();
            exporteur.setConfiguration(configuration);
            exporteur.exportReport();
            return sortie.toByteArray();
        } catch (JRException erreur) {
            throw new RenduImpossibleException(
                    "Export PDF impossible pour " + cheminJrxml, erreur);
        }
    }

    /**
     * Rend le meme modele en DOCX, pour relire un brouillon.
     *
     * <p>Les corrections ne se font jamais dans ce Word : elles se font dans
     * les formulaires de la plateforme, et le PDF est regenere depuis la base.
     * C'est la seule facon de garantir zero ecart entre la base et le dossier.
     */
    public byte[] versDocx(String cheminJrxml,
                           Map<String, Object> parametres,
                           Collection<?> lignes) {
        JasperPrint impression = remplir(cheminJrxml, parametres, lignes);

        try {
            var sortie = new ByteArrayOutputStream();
            JRDocxExporter exporteur = new JRDocxExporter();
            exporteur.setExporterInput(new SimpleExporterInput(impression));
            exporteur.setExporterOutput(new SimpleOutputStreamExporterOutput(sortie));

            var configuration = new SimpleDocxReportConfiguration();
            configuration.setFlexibleRowHeight(true);
            exporteur.setConfiguration(configuration);
            exporteur.exportReport();
            return sortie.toByteArray();
        } catch (JRException erreur) {
            throw new RenduImpossibleException(
                    "Export DOCX impossible pour " + cheminJrxml, erreur);
        }
    }

    public JasperPrint remplir(String cheminJrxml,
                               Map<String, Object> parametres,
                               Collection<?> lignes) {
        JasperReport modele = modele(cheminJrxml);
        JRDataSource donnees = new JRBeanCollectionDataSource(
                lignes == null ? List.of() : lignes);

        try {
            return JasperFillManager.fillReport(modele, parametres, donnees);
        } catch (JRException erreur) {
            throw new RenduImpossibleException(
                    "Remplissage impossible pour " + cheminJrxml, erreur);
        }
    }

    private JasperReport modele(String cheminJrxml) {
        return compiles.computeIfAbsent(cheminJrxml, chemin -> {
            try (InputStream source = new ClassPathResource(chemin).getInputStream()) {
                LOG.info("Compilation du modele {}", chemin);
                return JasperCompileManager.compileReport(source);
            } catch (Exception erreur) {
                throw new RenduImpossibleException(
                        "Modele introuvable ou invalide : " + chemin, erreur);
            }
        });
    }
}
