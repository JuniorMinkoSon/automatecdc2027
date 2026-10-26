package ci.smartex.offres.service;

import ci.smartex.offres.config.ProprietesApp;
import ci.smartex.offres.securite.ContexteOrganisation;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Les fichiers : ranges par organisation, chiffres au repos.
 *
 * <p>Le chemin logique ne contient jamais que l'identifiant de l'organisation
 * et un nom tire au sort. Le nom d'origine, qui porte souvent le nom du marche
 * ou de l'entreprise, ne sert qu'a l'affichage et reste en base.
 *
 * <p>Chiffrement AES-GCM, un vecteur d'initialisation neuf par fichier, ecrit
 * en tete du fichier chiffre. La cle vient de l'environnement et n'est nulle
 * part ailleurs : perdue, plus aucun DAO ni aucun PDF archive n'est lisible.
 * C'est dit dans .env.example, et c'est a prendre au mot.
 */
@Service
public class ServiceStockage {

    private static final String ALGORITHME = "AES/GCM/NoPadding";
    private static final int TAILLE_VECTEUR = 12;
    private static final int TAILLE_ETIQUETTE_BITS = 128;

    private final Path racine;
    private final SecretKey cle;
    private final SecureRandom alea = new SecureRandom();

    public ServiceStockage(ProprietesApp proprietes) throws IOException {
        this.racine = Path.of(proprietes.getStockageRacine()).toAbsolutePath().normalize();
        Files.createDirectories(racine);
        this.cle = deriverCle(proprietes.getCleChiffrementFichiers());
    }

    /**
     * Range un fichier dans l'espace de l'organisation courante et rend son
     * chemin logique, celui qui part en base.
     *
     * @param categorie sous-dossier metier : dao, coffre, personnel, sorties
     */
    public String deposer(String categorie, String nomOrigine, InputStream contenu)
            throws IOException {

        UUID organisation = ContexteOrganisation.exigerOrganisation();
        String extension = extension(nomOrigine);
        String nom = UUID.randomUUID() + extension;
        String cheminLogique = organisation + "/" + nettoyer(categorie) + "/" + nom;

        Path destination = resoudre(cheminLogique);
        Files.createDirectories(destination.getParent());

        // Ecriture sur un fichier temporaire puis deplacement atomique : une
        // coupure de courant laisse l'ancien etat, jamais un fichier a moitie
        // chiffre qu'on ne saura plus relire.
        Path temporaire = Files.createTempFile(destination.getParent(), "depot-", ".tmp");
        try {
            byte[] vecteur = new byte[TAILLE_VECTEUR];
            alea.nextBytes(vecteur);

            try (OutputStream sortie = Files.newOutputStream(temporaire)) {
                sortie.write(vecteur);
                Cipher chiffreur = Cipher.getInstance(ALGORITHME);
                chiffreur.init(Cipher.ENCRYPT_MODE, cle,
                        new GCMParameterSpec(TAILLE_ETIQUETTE_BITS, vecteur));
                try (var flux = new javax.crypto.CipherOutputStream(sortie, chiffreur)) {
                    contenu.transferTo(flux);
                }
            }
            Files.move(temporaire, destination,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception erreur) {
            Files.deleteIfExists(temporaire);
            throw new IOException("Depot du fichier impossible : " + nomOrigine, erreur);
        }

        return cheminLogique;
    }

    public String deposer(String categorie, String nomOrigine, byte[] contenu) throws IOException {
        return deposer(categorie, nomOrigine, new java.io.ByteArrayInputStream(contenu));
    }

    /** Relit un fichier en clair. Refuse tout chemin hors de l'organisation courante. */
    public byte[] lire(String cheminLogique) throws IOException {
        verifierAppartenance(cheminLogique);
        Path source = resoudre(cheminLogique);

        byte[] chiffre = Files.readAllBytes(source);
        if (chiffre.length < TAILLE_VECTEUR) {
            throw new IOException("Fichier tronque : " + cheminLogique);
        }

        try {
            byte[] vecteur = java.util.Arrays.copyOf(chiffre, TAILLE_VECTEUR);
            Cipher dechiffreur = Cipher.getInstance(ALGORITHME);
            dechiffreur.init(Cipher.DECRYPT_MODE, cle,
                    new GCMParameterSpec(TAILLE_ETIQUETTE_BITS, vecteur));
            return dechiffreur.doFinal(chiffre, TAILLE_VECTEUR, chiffre.length - TAILLE_VECTEUR);
        } catch (Exception erreur) {
            throw new IOException("Dechiffrement impossible. La cle a-t-elle change ? "
                    + cheminLogique, erreur);
        }
    }

    public boolean existe(String cheminLogique) {
        return Files.exists(resoudre(cheminLogique));
    }

    /** Empreinte du contenu en clair, celle qui prouve ce qui a ete valide. */
    public static String empreinte(byte[] contenu) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(contenu));
        } catch (Exception erreur) {
            throw new IllegalStateException("SHA-256 indisponible.", erreur);
        }
    }

    // ------------------------------------------------------------------ interne

    /**
     * Un chemin lu en base reste une donnee : il est verifie avant usage.
     * Sans cela, un chemin trafique traverserait l'isolation que la base vient
     * d'appliquer.
     */
    private void verifierAppartenance(String cheminLogique) {
        if (ContexteOrganisation.estSuperAdmin()) {
            return;
        }
        String attendu = ContexteOrganisation.exigerOrganisation() + "/";
        if (!cheminLogique.startsWith(attendu)) {
            throw new SecurityException(
                    "Ce fichier n'appartient pas a l'organisation courante.");
        }
    }

    private Path resoudre(String cheminLogique) {
        Path resolu = racine.resolve(cheminLogique).normalize();
        if (!resolu.startsWith(racine)) {
            throw new SecurityException("Chemin hors du stockage : " + cheminLogique);
        }
        return resolu;
    }

    private static String nettoyer(String segment) {
        return segment.replaceAll("[^a-zA-Z0-9_-]", "");
    }

    private static String extension(String nom) {
        if (nom == null) {
            return "";
        }
        int point = nom.lastIndexOf('.');
        if (point < 0 || point == nom.length() - 1) {
            return "";
        }
        String brute = nom.substring(point + 1).toLowerCase();
        return brute.matches("[a-z0-9]{1,8}") ? "." + brute : "";
    }

    private static SecretKey deriverCle(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "APP_CLE_CHIFFREMENT_FICHIERS n'est pas renseignee : les fichiers ne "
                            + "seraient pas chiffres au repos.");
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return new SecretKeySpec(sha.digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception erreur) {
            throw new IllegalStateException("Derivation de la cle impossible.", erreur);
        }
    }
}
