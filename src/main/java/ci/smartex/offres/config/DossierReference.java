package ci.smartex.offres.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * La forme du fichier de dossier de reference, lu au demarrage.
 *
 * <p>Ce contenu ne vit pas dans le code. Une raison sociale, un registre de
 * commerce, un numero de telephone et un mot de passe ecrits en dur partent
 * dans Git, et Git n'oublie pas : le depot devient alors le document le plus
 * sensible du projet, et chaque personne qui le clone en emporte une copie.
 * Le fichier, lui, reste sur la machine qui en a besoin.
 *
 * <p>Rien n'est invente ici : la plateforme ne fabrique pas d'entreprise, pas
 * de reference et pas de justificatif. Elle lit ce qu'on lui donne, et ce qui
 * manque reste manquant - ce sont les controles bloquants qui le diront.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DossierReference(
        String organisation,
        Compte compte,
        Entreprise entreprise,
        Representant representant,
        AppelOffres appelOffres,
        List<Lot> lots,
        List<Exigence> exigences) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Compte(String courriel, String nomComplet, String motDePasse) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entreprise(String raisonSociale, String sigle, String rccm,
                             String adresse, String boitePostale, String telephones,
                             String email) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Representant(String nom, String fonction) {
    }

    /**
     * {@code dateLimiteDepot} se lit en heure d'Abidjan, au format
     * {@code 2026-08-14T23:59}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AppelOffres(String numeroAo, String intitule, String autoriteContractante,
                              String maitreOuvrage, String destinataire, String financement,
                              Integer validiteOffreJours, String dateLimiteDepot,
                              String erreurAnalyse, String sourceDao) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Lot(Integer numero, String intitule, String localite,
                      Integer delaiMois, Long montantGarantie) {
    }

    /** {@code type} vaut PERSONNEL, MATERIEL ou PIECE. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Exigence(String type, String libelle, String valeur,
                           Integer quantiteMin, Integer anneesMin) {
    }

    /**
     * Dit ce qui manque pour que le chargement ait un sens, plutot que de
     * laisser Hibernate echouer plus tard sur une colonne nulle.
     */
    public String premierManque() {
        if (compte == null || estVide(compte.courriel())) {
            return "compte.courriel";
        }
        if (estVide(compte.motDePasse())) {
            return "compte.motDePasse";
        }
        if (entreprise == null || estVide(entreprise.raisonSociale())) {
            return "entreprise.raisonSociale";
        }
        if (appelOffres == null || estVide(appelOffres.intitule())) {
            return "appelOffres.intitule";
        }
        return null;
    }

    private static boolean estVide(String valeur) {
        return valeur == null || valeur.isBlank();
    }
}
