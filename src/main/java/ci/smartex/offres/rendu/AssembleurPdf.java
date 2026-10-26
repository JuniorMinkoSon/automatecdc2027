package ci.smartex.offres.rendu;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Assemble le dossier final : pieces generees et pieces du coffre dans l'ordre
 * du sommaire, puis filigrane tant que le dossier n'est pas valide.
 *
 * <p>PDFBox plutot que qpdf en ligne de commande : la fusion reste dans
 * l'application, sans processus externe a lancer ni fichier temporaire a
 * nettoyer, et surtout sans ecrire en clair sur le disque un dossier qu'on
 * vient de dechiffrer.
 */
@Service
public class AssembleurPdf {

    private static final String MENTION = "BROUILLON";

    /** Fusionne plusieurs PDF dans l'ordre donne. */
    public byte[] fusionner(List<byte[]> documents) {
        if (documents.isEmpty()) {
            throw new RenduImpossibleException("Rien a fusionner.", null);
        }
        if (documents.size() == 1) {
            return documents.get(0);
        }

        var sortie = new ByteArrayOutputStream();
        PDFMergerUtility fusion = new PDFMergerUtility();
        fusion.setDestinationStream(sortie);
        try {
            for (byte[] document : documents) {
                fusion.addSource(new RandomAccessReadBuffer(document));
            }
            fusion.mergeDocuments(null);
            return sortie.toByteArray();
        } catch (IOException erreur) {
            throw new RenduImpossibleException("Fusion du dossier impossible.", erreur);
        }
    }

    /**
     * Pose le filigrane BROUILLON en diagonale sur chaque page.
     *
     * <p>Il tombe a la validation, et seulement a la validation : un dossier
     * imprime sans filigrane est un dossier que quelqu'un a relu et signe.
     */
    public byte[] filigraner(byte[] pdf) {
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            var police = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            var transparence = new PDExtendedGraphicsState();
            transparence.setNonStrokingAlphaConstant(0.18f);

            for (PDPage page : document.getPages()) {
                var boite = page.getMediaBox();
                float taille = 96f;
                float largeurTexte = police.getStringWidth(MENTION) / 1000 * taille;

                try (var flux = new PDPageContentStream(document, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    flux.setGraphicsStateParameters(transparence);
                    flux.setNonStrokingColor(new java.awt.Color(200, 60, 60));
                    flux.beginText();
                    flux.setFont(police, taille);
                    // Diagonale depuis le coin bas gauche, centree sur la page.
                    flux.setTextMatrix(org.apache.pdfbox.util.Matrix.getRotateInstance(
                            Math.toRadians(45),
                            (boite.getWidth() - largeurTexte * 0.7f) / 2,
                            boite.getHeight() / 4));
                    flux.showText(MENTION);
                    flux.endText();
                }
            }

            var sortie = new ByteArrayOutputStream();
            document.save(sortie);
            return sortie.toByteArray();
        } catch (IOException erreur) {
            throw new RenduImpossibleException("Pose du filigrane impossible.", erreur);
        }
    }

    public int nombreDePages(byte[] pdf) {
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        } catch (IOException erreur) {
            throw new RenduImpossibleException("Lecture du PDF impossible.", erreur);
        }
    }
}
