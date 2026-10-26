package ci.smartex.offres.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Reglages de la plateforme. Les secrets viennent de l'environnement, jamais du
 * code ni de la base.
 */
@ConfigurationProperties(prefix = "app")
@Validated
public class ProprietesApp {

    /** Racine du stockage des fichiers, rangee par organisation. */
    @NotBlank
    private String stockageRacine = "/var/automatecdc/fichiers";

    /** URL publique, pour les liens des courriels. */
    @NotBlank
    private String urlPublique = "http://localhost:8080";

    /** Signe les liens de telechargement a duree courte. */
    @NotBlank
    private String cleLiensSignes = "";

    /**
     * Chiffre les fichiers au repos. Irremplacable : perdue, aucun DAO ni aucun
     * PDF archive ne peut plus etre relu.
     */
    @NotBlank
    private String cleChiffrementFichiers = "";

    /** Duree de validite d'un lien de telechargement signe. */
    private Duration dureeLienSigne = Duration.ofMinutes(10);

    private final Ocr ocr = new Ocr();
    private final Gotenberg gotenberg = new Gotenberg();
    private final Ia ia = new Ia();
    private final Paiement paiement = new Paiement();
    private final Veille veille = new Veille();

    public static class Ocr {
        private String url = "http://ocr:5000";
        private Duration delai = Duration.ofMinutes(10);

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Duration getDelai() {
            return delai;
        }

        public void setDelai(Duration delai) {
            this.delai = delai;
        }
    }

    public static class Gotenberg {
        private String url = "http://gotenberg:3000";
        private Duration delai = Duration.ofMinutes(3);

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Duration getDelai() {
            return delai;
        }

        public void setDelai(Duration delai) {
            this.delai = delai;
        }
    }

    public static class Ia {
        /** Modele Claude utilise pour l'extraction. */
        private String modele = "claude-opus-5-5";

        /** Effort de raisonnement : l'extraction d'un DAO merite mieux que le minimum. */
        private String effort = "high";

        private int maxTokens = 32000;

        /** Coupe-circuit : a faux, aucun appel n'est emis et l'extraction reste manuelle. */
        private boolean active = true;

        public String getModele() {
            return modele;
        }

        public void setModele(String modele) {
            this.modele = modele;
        }

        public String getEffort() {
            return effort;
        }

        public void setEffort(String effort) {
            this.effort = effort;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }

    public static class Paiement {
        /** Verifie la signature des confirmations de l'agregateur. */
        private String secretSignature = "";

        /** Tolerance sur l'horodatage de la notification, contre le rejeu. */
        private Duration fenetreAntiRejeu = Duration.ofMinutes(5);

        public String getSecretSignature() {
            return secretSignature;
        }

        public void setSecretSignature(String secretSignature) {
            this.secretSignature = secretSignature;
        }

        public Duration getFenetreAntiRejeu() {
            return fenetreAntiRejeu;
        }

        public void setFenetreAntiRejeu(Duration fenetreAntiRejeu) {
            this.fenetreAntiRejeu = fenetreAntiRejeu;
        }
    }

    public static class Veille {
        /** Fenetre d'alerte sur les pieces qui expirent. */
        private int joursAvantExpiration = 30;

        public int getJoursAvantExpiration() {
            return joursAvantExpiration;
        }

        public void setJoursAvantExpiration(int joursAvantExpiration) {
            this.joursAvantExpiration = joursAvantExpiration;
        }
    }

    public String getStockageRacine() {
        return stockageRacine;
    }

    public void setStockageRacine(String stockageRacine) {
        this.stockageRacine = stockageRacine;
    }

    public String getUrlPublique() {
        return urlPublique;
    }

    public void setUrlPublique(String urlPublique) {
        this.urlPublique = urlPublique;
    }

    public String getCleLiensSignes() {
        return cleLiensSignes;
    }

    public void setCleLiensSignes(String cleLiensSignes) {
        this.cleLiensSignes = cleLiensSignes;
    }

    public String getCleChiffrementFichiers() {
        return cleChiffrementFichiers;
    }

    public void setCleChiffrementFichiers(String cleChiffrementFichiers) {
        this.cleChiffrementFichiers = cleChiffrementFichiers;
    }

    public Duration getDureeLienSigne() {
        return dureeLienSigne;
    }

    public void setDureeLienSigne(Duration dureeLienSigne) {
        this.dureeLienSigne = dureeLienSigne;
    }

    public Ocr getOcr() {
        return ocr;
    }

    public Gotenberg getGotenberg() {
        return gotenberg;
    }

    public Ia getIa() {
        return ia;
    }

    public Paiement getPaiement() {
        return paiement;
    }

    public Veille getVeille() {
        return veille;
    }
}
