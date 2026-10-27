package ci.smartex.offres.rendu;

import ci.smartex.offres.domain.Dossier;
import ci.smartex.offres.domain.Entreprise;
import ci.smartex.offres.util.MontantEnLettres;

import java.util.Map;

/**
 * Le corps des lettres types, en un seul endroit.
 *
 * <p>Ce sont des textes juridiques : ils engagent l'entreprise. Les garder ici
 * plutot que dans la base les met sous Git, donc sous revue et sous historique.
 * Une entreprise ne peut pas les modifier, et c'est voulu : un formulaire du
 * DAO ou quelqu'un aurait glisse une reserve n'est plus le formulaire du DAO.
 *
 * <p>Les durees et les montants viennent des parametres, jamais du texte :
 * c'est ce qui garantit que la lettre dit la meme chose que le dossier.
 */
public final class TextesLettres {

    private TextesLettres() {
    }

    public static String titre(String codeModele) {
        return switch (codeModele) {
            case "DELAI_EXECUTION" -> "Engagement sur le délai d'exécution";
            case "VALIDITE_OFFRE" -> "Engagement sur la validité de l'offre";
            case "VALIDITE_GARANTIE" -> "Engagement sur la validité de la garantie";
            case "POUVOIRS" -> "Pouvoirs du signataire";
            case "CONFLIT_INTERETS" -> "Déclaration relative aux conflits d'intérêts";
            case "DECLARATION_HONNEUR" -> "Déclaration sur l'honneur";
            default -> codeModele;
        };
    }

    public static String corps(String codeModele,
                               Dossier dossier,
                               Entreprise entreprise,
                               Map<String, Object> contexte) {

        String nom = entreprise.getRaisonSociale();
        String signataire = String.valueOf(contexte.getOrDefault("SIGNATAIRE_NOM", ""));
        String fonction = String.valueOf(contexte.getOrDefault("SIGNATAIRE_FONCTION", ""));

        return switch (codeModele) {
            case "DELAI_EXECUTION" -> dossier.getDelaiExecutionMois() == null
                    ? "Le délai d'exécution n'a pas été renseigné."
                    : """
                      Je soussigné, %s, agissant en qualité de %s de l'entreprise %s, \
                      déclare avoir pris connaissance du délai d'exécution fixé par le dossier \
                      d'appel d'offres.

                      À ce titre, je m'engage à exécuter l'intégralité des travaux objet du \
                      présent marché dans un délai de %s (%d) mois à compter de la date de \
                      notification de l'ordre de service de démarrage.

                      Je reconnais que tout dépassement de ce délai m'expose aux pénalités \
                      prévues au cahier des clauses administratives particulières.
                      """.formatted(signataire, fonction, nom,
                              MontantEnLettres.nombre(dossier.getDelaiExecutionMois()),
                              dossier.getDelaiExecutionMois());

            case "VALIDITE_OFFRE" -> dossier.getValiditeOffreJours() == null
                    ? "La durée de validité de l'offre n'a pas été renseignée."
                    : """
                      Je soussigné, %s, agissant en qualité de %s de l'entreprise %s, \
                      déclare que la présente offre demeure valable et m'engage pour une durée \
                      de %s (%d) jours à compter de la date limite de dépôt des offres.

                      Pendant toute cette période, je m'interdis de la retirer ou d'en modifier \
                      les termes.
                      """.formatted(signataire, fonction, nom,
                              MontantEnLettres.nombre(dossier.getValiditeOffreJours()),
                              dossier.getValiditeOffreJours());

            case "VALIDITE_GARANTIE" -> dossier.getValiditeGarantieJours() == null
                    ? "La durée de validité de la garantie n'a pas été renseignée."
                    : """
                      Je soussigné, %s, agissant en qualité de %s de l'entreprise %s, \
                      déclare que la garantie de soumission jointe à la présente offre est \
                      valable %s (%d) jours à compter de la date limite de dépôt des offres, \
                      soit la durée de validité de l'offre augmentée de trente jours, \
                      conformément aux instructions aux candidats.
                      """.formatted(signataire, fonction, nom,
                              MontantEnLettres.nombre(dossier.getValiditeGarantieJours()),
                              dossier.getValiditeGarantieJours());

            case "POUVOIRS" -> """
                      Je soussigné, %s, %s de l'entreprise %s, certifie disposer, en vertu des \
                      statuts de la société et des décisions de ses organes sociaux, de tous \
                      les pouvoirs nécessaires pour engager celle-ci dans le cadre du présent \
                      appel d'offres.

                      Ces pouvoirs comprennent notamment la signature de l'offre technique et \
                      de l'offre financière, la souscription de tout engagement s'y rapportant \
                      et, le cas échéant, la signature du marché.
                      """.formatted(signataire, fonction, nom);

            case "CONFLIT_INTERETS" -> """
                      Je soussigné, %s, %s de l'entreprise %s, déclare sur l'honneur que ni \
                      l'entreprise, ni ses dirigeants, ni son personnel ne se trouvent dans une \
                      situation de conflit d'intérêts au sens de la réglementation des marchés \
                      publics.

                      En particulier, l'entreprise n'a participé ni à la préparation du dossier \
                      d'appel d'offres, ni aux études préalables du présent marché, et n'est \
                      liée à aucun autre soumissionnaire du présent appel d'offres.

                      Je m'engage à signaler sans délai à l'autorité contractante toute \
                      situation de cette nature qui viendrait à se présenter.
                      """.formatted(signataire, fonction, nom);

            case "DECLARATION_HONNEUR" -> """
                      Je soussigné, %s, %s de l'entreprise %s, déclare sur l'honneur :

                      - que l'entreprise n'est ni en état de liquidation, ni en redressement \
                      judiciaire, ni dans une situation équivalente ;
                      - qu'elle est à jour de ses obligations fiscales et sociales ;
                      - qu'elle n'a fait l'objet d'aucune condamnation définitive pour un délit \
                      affectant sa moralité professionnelle ;
                      - qu'elle ne figure sur aucune liste d'exclusion des marchés publics ;
                      - que les renseignements et les pièces fournis à l'appui de la présente \
                      offre sont exacts et sincères.

                      Je suis informé que toute déclaration inexacte entraîne le rejet de \
                      l'offre et peut donner lieu à des poursuites.
                      """.formatted(signataire, fonction, nom);

            default -> "";
        };
    }
}
