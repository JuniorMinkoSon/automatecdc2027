package ci.smartex.offres.extraction;

import ci.smartex.offres.config.ProprietesApp;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Conversion bureautique par Gotenberg, qui porte LibreOffice.
 *
 * <p>Les DAO arrivent le plus souvent en .doc : les convertir en PDF est la
 * premiere etape avant tout OCR et toute extraction.
 */
@Component
public class ClientGotenberg {

    private final HttpClient client;
    private final ProprietesApp proprietes;

    public ClientGotenberg(ProprietesApp proprietes) {
        this.proprietes = proprietes;
        this.client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
    }

    /** Convertit un document bureautique (.doc, .docx, .odt, .xls...) en PDF. */
    public byte[] versPdf(String nomFichier, byte[] contenu) throws IOException {
        String frontiere = "----automatecdc" + UUID.randomUUID();
        byte[] corps = corpsMultipart(frontiere, nomFichier, contenu);

        HttpRequest requete = HttpRequest.newBuilder()
                .uri(URI.create(proprietes.getGotenberg().getUrl()
                        + "/forms/libreoffice/convert"))
                .timeout(proprietes.getGotenberg().getDelai())
                .header("Content-Type", "multipart/form-data; boundary=" + frontiere)
                .POST(HttpRequest.BodyPublishers.ofByteArray(corps))
                .build();

        try {
            HttpResponse<byte[]> reponse =
                    client.send(requete, HttpResponse.BodyHandlers.ofByteArray());
            if (reponse.statusCode() != 200) {
                throw new IOException("Gotenberg a repondu " + reponse.statusCode() + " : "
                        + new String(reponse.body(), StandardCharsets.UTF_8));
            }
            return reponse.body();
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new IOException("Conversion interrompue.", interruption);
        }
    }

    private byte[] corpsMultipart(String frontiere, String nomFichier, byte[] contenu)
            throws IOException {

        var sortie = new ByteArrayOutputStream();
        String entete = "--" + frontiere + "\r\n"
                + "Content-Disposition: form-data; name=\"files\"; filename=\""
                + nomFichier.replace("\"", "") + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";

        sortie.write(entete.getBytes(StandardCharsets.UTF_8));
        sortie.write(contenu);
        sortie.write(("\r\n--" + frontiere + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return sortie.toByteArray();
    }
}
