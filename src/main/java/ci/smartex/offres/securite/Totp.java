package ci.smartex.offres.securite;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

/**
 * Second facteur a usage unique, RFC 6238.
 *
 * <p>Obligatoire pour le super-administrateur, l'operateur mandate,
 * l'administrateur d'entreprise et le validateur : ce sont les quatre roles
 * qui peuvent engager l'entreprise ou toucher a l'argent.
 *
 * <p>Implementee ici plutot qu'importee : trente lignes de HMAC valent mieux
 * qu'une dependance de plus a suivre sur un VPS qu'on veut garder simple.
 */
public final class Totp {

    private static final String ALPHABET_BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int PAS_SECONDES = 30;
    private static final int CHIFFRES = 6;
    private static final int MODULO = 1_000_000;

    /**
     * Tolerance d'un pas de part et d'autre : les horloges des telephones
     * derivent, et refuser un code juste a trois secondes pres se paie en
     * appels au support.
     */
    private static final int FENETRE = 1;

    private static final SecureRandom ALEA = new SecureRandom();

    private Totp() {
    }

    /** Un secret partage, a montrer une seule fois sous forme de QR code. */
    public static String nouveauSecret() {
        byte[] octets = new byte[20];
        ALEA.nextBytes(octets);
        return encoderBase32(octets);
    }

    /** L'URI otpauth: a transformer en QR code pour l'application du telephone. */
    public static String uriProvisionnement(String secret, String email, String emetteur) {
        return "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30"
                .formatted(urlEncode(emetteur), urlEncode(email), secret, urlEncode(emetteur));
    }

    public static boolean verifier(String secret, String code) {
        return verifier(secret, code, Instant.now());
    }

    static boolean verifier(String secret, String code, Instant instant) {
        if (secret == null || code == null) {
            return false;
        }
        String propre = code.replaceAll("\\s", "");
        if (propre.length() != CHIFFRES || !propre.chars().allMatch(Character::isDigit)) {
            return false;
        }

        byte[] cle = decoderBase32(secret);
        long compteur = instant.getEpochSecond() / PAS_SECONDES;

        boolean trouve = false;
        for (int decalage = -FENETRE; decalage <= FENETRE; decalage++) {
            // Comparaison a temps constant sur toute la fenetre : on ne sort pas
            // de la boucle au premier succes, pour ne rien dire par la duree.
            trouve |= egalConstant(propre, calculer(cle, compteur + decalage));
        }
        return trouve;
    }

    static String calculer(byte[] cle, long compteur) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(cle, "HmacSHA1"));
            byte[] empreinte = mac.doFinal(ByteBuffer.allocate(8).putLong(compteur).array());

            int depart = empreinte[empreinte.length - 1] & 0x0F;
            int binaire = ((empreinte[depart] & 0x7F) << 24)
                    | ((empreinte[depart + 1] & 0xFF) << 16)
                    | ((empreinte[depart + 2] & 0xFF) << 8)
                    | (empreinte[depart + 3] & 0xFF);

            return "%06d".formatted(binaire % MODULO);
        } catch (Exception erreur) {
            throw new IllegalStateException("Calcul du code a usage unique impossible.", erreur);
        }
    }

    private static boolean egalConstant(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < a.length(); i++) {
            difference |= a.charAt(i) ^ b.charAt(i);
        }
        return difference == 0;
    }

    static String encoderBase32(byte[] octets) {
        StringBuilder sortie = new StringBuilder();
        int tampon = 0;
        int bits = 0;
        for (byte octet : octets) {
            tampon = (tampon << 8) | (octet & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sortie.append(ALPHABET_BASE32.charAt((tampon >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sortie.append(ALPHABET_BASE32.charAt((tampon << (5 - bits)) & 0x1F));
        }
        return sortie.toString();
    }

    static byte[] decoderBase32(String texte) {
        String propre = texte.toUpperCase(Locale.ROOT).replaceAll("[^A-Z2-7]", "");
        byte[] sortie = new byte[propre.length() * 5 / 8];
        int tampon = 0;
        int bits = 0;
        int position = 0;
        for (int i = 0; i < propre.length(); i++) {
            int valeur = ALPHABET_BASE32.indexOf(propre.charAt(i));
            if (valeur < 0) {
                throw new IllegalArgumentException("Secret a usage unique mal forme.");
            }
            tampon = (tampon << 5) | valeur;
            bits += 5;
            if (bits >= 8) {
                sortie[position++] = (byte) ((tampon >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return sortie;
    }

    private static String urlEncode(String valeur) {
        return java.net.URLEncoder.encode(valeur, java.nio.charset.StandardCharsets.UTF_8);
    }
}
