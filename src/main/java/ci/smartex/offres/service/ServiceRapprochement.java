package ci.smartex.offres.service;

import ci.smartex.offres.domain.*;
import ci.smartex.offres.repo.*;
import ci.smartex.offres.securite.ContexteOrganisation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;

/**
 * Rapproche le personnel et le materiel de l'entreprise des exigences du DAO.
 *
 * <p>Le rapprochement propose, il ne decide pas. Chaque affectation est posee
 * avec {@code proposeAutomatiquement} a vrai ; l'utilisateur la confirme ou la
 * change, et le drapeau tombe. Rien de ce qui part au depot n'aura donc ete
 * choisi par la machine sans qu'un humain l'ait vu.
 *
 * <p>Il ne touche jamais a une affectation que quelqu'un a deja corrigee : une
 * relance apres modification du DAO ne doit pas effacer un arbitrage humain.
 */
@Service
public class ServiceRapprochement {

    private final DepotExigence depotExigence;
    private final DepotPersonnel depotPersonnel;
    private final DepotMateriel depotMateriel;
    private final DepotAffectationPersonnel depotAffectationPersonnel;
    private final DepotAffectationMateriel depotAffectationMateriel;
    private final DepotDossier depotDossier;
    private final ServiceJournal journal;

    public ServiceRapprochement(DepotExigence depotExigence,
                                DepotPersonnel depotPersonnel,
                                DepotMateriel depotMateriel,
                                DepotAffectationPersonnel depotAffectationPersonnel,
                                DepotAffectationMateriel depotAffectationMateriel,
                                DepotDossier depotDossier,
                                ServiceJournal journal) {
        this.depotExigence = depotExigence;
        this.depotPersonnel = depotPersonnel;
        this.depotMateriel = depotMateriel;
        this.depotAffectationPersonnel = depotAffectationPersonnel;
        this.depotAffectationMateriel = depotAffectationMateriel;
        this.depotDossier = depotDossier;
        this.journal = journal;
    }

    @Transactional
    public Resultat proposer(UUID dossierId) {
        Dossier dossier = depotDossier.findById(dossierId).orElseThrow(
                () -> new IllegalArgumentException("Dossier inconnu."));
        UUID organisation = ContexteOrganisation.exigerOrganisation();

        List<Exigence> exigences = depotExigence
                .findAllByAppelOffresId(dossier.getAppelOffresId()).stream()
                .filter(e -> e.getLotId() == null || e.getLotId().equals(dossier.getLotId()))
                .toList();

        // Les personnes deja engagees sur un autre lot du meme appel ne sont
        // pas proposees : le DAO exige un personnel distinct entre lots, autant
        // ne pas suggerer ce qu'un controle bloquant refusera ensuite.
        Set<UUID> indisponibles = personnelEngageAilleurs(dossier);

        int personnesProposees = rapprocherPersonnel(dossier, organisation, exigences, indisponibles);
        int materielsProposes = rapprocherMateriel(dossier, organisation, exigences);

        List<String> nonPourvues = exigencesNonPourvues(dossierId, exigences);

        journal.enregistrer("RAPPROCHEMENT", "dossier", dossierId,
                personnesProposees + " personne(s), " + materielsProposes + " materiel(s)");

        return new Resultat(personnesProposees, materielsProposes, nonPourvues);
    }

    // ------------------------------------------------------------------ personnel

    private int rapprocherPersonnel(Dossier dossier,
                                    UUID organisation,
                                    List<Exigence> exigences,
                                    Set<UUID> indisponibles) {

        List<AffectationPersonnel> existantes =
                depotAffectationPersonnel.findAllByDossierId(dossier.getId());

        Set<String> postesDejaTraites = new HashSet<>();
        for (AffectationPersonnel affectation : existantes) {
            postesDejaTraites.add(cle(affectation.getPosteExige()));
        }

        // Une personne ne peut pas occuper deux postes du meme lot.
        Set<UUID> dejaPrises = new HashSet<>(indisponibles);
        existantes.forEach(a -> dejaPrises.add(a.getPersonnelId()));

        List<Personnel> candidats = depotPersonnel.findAllByOrganisationId(organisation);
        int proposees = 0;

        for (Exigence exigence : exigences) {
            if (exigence.getType() != Exigence.Type.PERSONNEL) {
                continue;
            }
            if (postesDejaTraites.contains(cle(exigence.getLibelle()))) {
                continue;
            }

            Optional<Personnel> meilleur = candidats.stream()
                    .filter(personne -> !dejaPrises.contains(personne.getId()))
                    .filter(personne -> correspond(personne.getPoste(), exigence.getLibelle()))
                    .filter(personne -> exigence.getAnneesMin() == null
                            || personne.getAnneesExperience() >= exigence.getAnneesMin())
                    // A poste egal, la personne la plus experimentee : c'est
                    // elle qui tiendra le mieux devant la commission.
                    .max(Comparator.comparingInt(Personnel::getAnneesExperience));

            if (meilleur.isEmpty()) {
                continue;
            }

            AffectationPersonnel affectation = new AffectationPersonnel();
            affectation.setOrganisationId(organisation);
            affectation.setDossierId(dossier.getId());
            affectation.setPersonnelId(meilleur.get().getId());
            affectation.setExigenceId(exigence.getId());
            affectation.setPosteExige(exigence.getLibelle());
            affectation.setProposeAutomatiquement(true);
            depotAffectationPersonnel.save(affectation);

            dejaPrises.add(meilleur.get().getId());
            postesDejaTraites.add(cle(exigence.getLibelle()));
            proposees++;
        }

        return proposees;
    }

    private Set<UUID> personnelEngageAilleurs(Dossier dossier) {
        return depotDossier.findAllByAppelOffresId(dossier.getAppelOffresId()).stream()
                .filter(autre -> !autre.getId().equals(dossier.getId()))
                .flatMap(autre -> depotAffectationPersonnel
                        .findAllByDossierId(autre.getId()).stream())
                .map(AffectationPersonnel::getPersonnelId)
                .collect(java.util.stream.Collectors.toSet());
    }

    // ------------------------------------------------------------------ materiel

    private int rapprocherMateriel(Dossier dossier, UUID organisation, List<Exigence> exigences) {
        List<AffectationMateriel> existantes =
                depotAffectationMateriel.findAllByDossierId(dossier.getId());

        Set<String> designationsTraitees = new HashSet<>();
        existantes.forEach(a -> designationsTraitees.add(cle(a.getDesignationExigee())));

        Set<UUID> dejaPris = new HashSet<>();
        existantes.forEach(a -> dejaPris.add(a.getMaterielId()));

        List<Materiel> parc = depotMateriel.findAllByOrganisationId(organisation);
        int proposes = 0;

        for (Exigence exigence : exigences) {
            if (exigence.getType() != Exigence.Type.MATERIEL) {
                continue;
            }
            if (designationsTraitees.contains(cle(exigence.getLibelle()))) {
                continue;
            }

            int attendu = exigence.getQuantiteMin() == null ? 1 : exigence.getQuantiteMin();
            List<Materiel> retenus = parc.stream()
                    .filter(engin -> !dejaPris.contains(engin.getId()))
                    .filter(engin -> correspond(engin.getDesignation(), exigence.getLibelle()))
                    // Le justificatif d'abord : sans lui, le materiel ne compte
                    // pas pour la commission, meme s'il existe.
                    .sorted(Comparator.comparing(
                            (Materiel engin) -> engin.getJustificatifChemin() == null)
                            .thenComparing(engin -> engin.getAnnee() == null ? 0 : -engin.getAnnee()))
                    .limit(attendu)
                    .toList();

            for (Materiel engin : retenus) {
                AffectationMateriel affectation = new AffectationMateriel();
                affectation.setOrganisationId(organisation);
                affectation.setDossierId(dossier.getId());
                affectation.setMaterielId(engin.getId());
                affectation.setExigenceId(exigence.getId());
                affectation.setDesignationExigee(exigence.getLibelle());
                affectation.setProposeAutomatiquement(true);
                depotAffectationMateriel.save(affectation);

                dejaPris.add(engin.getId());
                proposes++;
            }
            designationsTraitees.add(cle(exigence.getLibelle()));
        }

        return proposes;
    }

    // ------------------------------------------------------------------ outils

    private List<String> exigencesNonPourvues(UUID dossierId, List<Exigence> exigences) {
        Set<String> pourvuesPersonnel = depotAffectationPersonnel.findAllByDossierId(dossierId)
                .stream().map(a -> cle(a.getPosteExige()))
                .collect(java.util.stream.Collectors.toSet());
        Set<String> pourvuesMateriel = depotAffectationMateriel.findAllByDossierId(dossierId)
                .stream().map(a -> cle(a.getDesignationExigee()))
                .collect(java.util.stream.Collectors.toSet());

        List<String> manquantes = new ArrayList<>();
        for (Exigence exigence : exigences) {
            boolean pourvue = switch (exigence.getType()) {
                case PERSONNEL -> pourvuesPersonnel.contains(cle(exigence.getLibelle()));
                case MATERIEL -> pourvuesMateriel.contains(cle(exigence.getLibelle()));
                default -> true;
            };
            if (!pourvue) {
                manquantes.add(exigence.getLibelle());
            }
        }
        return manquantes;
    }

    /**
     * Deux libelles correspondent si l'un contient l'autre, accents et casse
     * mis de cote. Le DAO ecrit « Conducteur des travaux » la ou la fiche de
     * l'entreprise dit « conducteur de travaux » : un rapprochement strict ne
     * trouverait rien et laisserait le formulaire vide, ce qu'on cherche
     * justement a eviter.
     */
    private boolean correspond(String aDroite, String aGauche) {
        if (aDroite == null || aGauche == null) {
            return false;
        }
        String a = cle(aDroite);
        String b = cle(aGauche);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.contains(b) || b.contains(a)) {
            return true;
        }
        // Sinon, au moins deux mots significatifs en commun.
        Set<String> motsA = new HashSet<>(List.of(a.split(" ")));
        Set<String> motsB = new HashSet<>(List.of(b.split(" ")));
        motsA.removeAll(MOTS_VIDES);
        motsB.retainAll(motsA);
        return motsB.size() >= 2;
    }

    private static final Set<String> MOTS_VIDES =
            Set.of("de", "des", "du", "le", "la", "les", "un", "une", "et", "a", "au", "aux");

    private String cle(String texte) {
        if (texte == null) {
            return "";
        }
        String sansAccents = Normalizer.normalize(texte, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sansAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Ce que le rapprochement a propose, et ce qu'il n'a pas su pourvoir.
     * Les exigences non pourvues sont affichees telles quelles : mieux vaut
     * dire qu'on n'a pas trouve que proposer quelqu'un au hasard.
     */
    public record Resultat(int personnesProposees,
                           int materielsProposes,
                           List<String> exigencesNonPourvues) {

        public boolean complet() {
            return exigencesNonPourvues.isEmpty();
        }
    }
}
