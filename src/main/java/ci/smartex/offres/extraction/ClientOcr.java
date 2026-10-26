package ci.smartex.offres.extraction;

import ci.smartex.offres.config.ProprietesApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Appelle le conteneur OCR, qui tourne sur le reseau interne et n'est joignable
 * de nulle part ailleurs.
 */
@Component
public class ClientOcr {

    private static final Logger LOG = LoggerFactory.getLogger(ClientOcr.class);

    private final HttpClient client;
    private final ProprietesApp proprietes;

    public ClientOcr(ProprietesApp proprietes) {
        this.proprietes = proprietes;
        this.client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
    }

    /** Ajoute une couche texte a un PDF scanne ; laisse intact un PDF deja textuel. */
    public byte[] couchesTexte(byte[] pdf) throws IOException {
        return appeler("/ocr", pdf).body();
    }

    /**
     * Le texte brut du document, avec la mise en page preservee : les tableaux
     * du DQE ne sont lisibles qu'a cette condition.
     */
    public String texte(byte[] pdf) throws IOException {
        return new String(appeler("/texte", pdf).body(), StandardCharsets.UTF_8);
    }

    public boolean disponible() {
        try {
            HttpRequest requete = HttpRequest.newBuilder()
                    .uri(URI.create(proprietes.getOcr().getUrl() + "/sante"))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();
            return client.send(requete, HttpResponse.BodyHandlers.discarding())
                    .statusCode() == 200;
        } catch (Exception erreur) {
            LOG.warn("Service OCR injoignable : {}", erreur.getMessage());
            return false;
        }
    }

    private HttpResponse<byte[]> appeler(String chemin, byte[] pdf) throws IOException {
        HttpRequest requete = HttpRequest.newBuilder()
                .uri(URI.create(proprietes.getOcr().getUrl() + chemin))
                .timeout(proprietes.getOcr().getDelai())
                .header("Content-Type", "application/pdf")
                .POST(HttpRequest.BodyPublishers.ofByteArray(pdf))
                .build();

        try {
            HttpResponse<byte[]> reponse =
                    client.send(requete, HttpResponse.BodyHandlers.ofByteArray());
            if (reponse.statusCode() != 200) {
                throw new IOException("Le service OCR a repondu " + reponse.statusCode() + " : "
                        + new String(reponse.body(), StandardCharsets.UTF_8));
            }
            return reponse;
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new IOException("Appel OCR interrompu.", interruption);
        }
    }
}
