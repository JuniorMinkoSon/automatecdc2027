package ci.smartex.offres.conformite;

import ci.smartex.offres.domain.AffectationPersonnel;
import ci.smartex.offres.domain.ControleResultat.Niveau;
import ci.smartex.offres.domain.Exigence;
import ci.smartex.offres.domain.Personnel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Personnel cle et materiel : ce que le dossier engage face aux exigences
 * extraites des sections 5 et 6 du DAO.
 *
 * <p>Dans l'offre NCHTP d'aout 2026, le formulaire PER-1 etait vide alors que
 * le DAO exigeait un conducteur des travaux et un chef de chantier par lot.
 * Ces deux controles sont la pour que cela ne reparte plus.
 */
public final class ControlesMoyens {

    private ControlesMoyens() {
    }

    @Component
    public static class PersonnelCle implements Controle {

        @Override
        public String code() {
            return "PERSONNEL_CLE";
        }

        @Override
        public String libelle() {
            return "Personnel cle";
        }

        @Override
        public Niveau niveau() {
            return Niveau.BLOQUANT;
        }

        @Override
        public boolean sApplique(DossierAControler d) {
            return !d.exigencesDeType(Exigence.Type.PERSONNEL).isEmpty();
        }

        @Override
        public Verdict verifier(DossierAControler d) {
            List<String> problemes = new ArrayList<>();
            String lien = "/dossiers/" + d.dossier().getId() + "/personnel";

            for (Exigence exigence : exigencesDuLot(d)) {
                Optional<AffectationPersonnel> affectation = d.affectationsPersonnel().stream()
                        .filter(a -> exigence.getLibelle().equalsIgnoreCase(a.getPosteExige()))
                        .findFirst();

                if (affectation.isEmpty()) {
                    problemes.add(exigence.getLibelle() + " manquant");
                    continue;
                }

                Optional<Personnel> personne = d.personnelParId(affectation.get().getPersonnelId());
                if (personne.isEmpty()) {
                    problemes.add(exigence.getLibelle() + " : la personne affectee est introuvable");
                    continue;
                }

                if (exigence.getAnneesMin() != null
                        && personne.get().getAnneesExperience() < exigence.getAnneesMin()) {
                    problemes.add(personne.get().getNom() + " totalise "
                            + personne.get().getAnneesExperience() + " ans alors que "
                            + exigence.getLibelle() + " en demande " + exigence.getAnneesMin());
                }

                if (personne.get().getCvChemin() == null) {
                    problemes.add("CV manquant pour " + personne.get().getNom());
                }
            }

            problemes.addAll(cumulsEntreLots(d));

            return problemes.isEmpty()
                    ? Verdict.conforme()
                    : Verdict.echec(String.join(" ; ", problemes) + ".", lien);
        }

        private List<Exigence> exigencesDuLot(DossierAControler d) {
            UUID lotId = d.dossier().getLotId();
            return d.exigencesDeType(Exigence.Type.PERSONNEL).stream()
                    .filter(e -> e.getLotId() == null || e.getLotId().equals(lotId))
                    .toList();
        }

        /**
         * Le DAO exige un personnel distinct entre lots : la meme personne ne
         * peut pas conduire deux chantiers a la fois, et une offre qui le
         * pretend est ecartee.
         */
        private List<String> cumulsEntreLots(DossierAControler d) {
            if (d.affectationsPersonnelAutresLots().isEmpty()) {
                return List.of();
            }

            Set<UUID> engagesAilleurs = d.affectationsPersonnelAutresLots().stream()
                    .map(AffectationPersonnel::getPersonnelId)
                    .collect(Collectors.toSet());

            return d.affectationsPersonnel().stream()
                    .filter(a -> engagesAilleurs.contains(a.getPersonnelId()))
                    .map(a -> d.personnelParId(a.getPersonnelId())
                            .map(Personnel::getNom)
                            .orElse("une personne"))
                    .distinct()
                    .map(nom -> nom + " est deja engage sur un autre lot du meme appel")
                    .toList();
        }
    }

    @Component
    public static class MaterielMinimum implements Controle {

        @Override
        public String code() {
            return "MATERIEL";
        }

        @Override
        public String libelle() {
            return "Materiel minimum";
        }

        @Override
        public Niveau niveau() {
            return Niveau.BLOQUANT;
        }

        @Override
        public boolean sApplique(DossierAControler d) {
            return !d.exigencesDeType(Exigence.Type.MATERIEL).isEmpty();
        }

        @Override
        public Verdict verifier(DossierAControler d) {
            List<String> problemes = new ArrayList<>();
            String lien = "/dossiers/" + d.dossier().getId() + "/materiel";
            UUID lotId = d.dossier().getLotId();

            for (Exigence exigence : d.exigencesDeType(Exigence.Type.MATERIEL)) {
                if (exigence.getLotId() != null && !exigence.getLotId().equals(lotId)) {
                    continue;
                }

                var affectations = d.affectationsMateriel().stream()
                        .filter(a -> exigence.getLibelle().equalsIgnoreCase(a.getDesignationExigee()))
                        .toList();

                int attendu = exigence.getQuantiteMin() == null ? 1 : exigence.getQuantiteMin();
                if (affectations.size() < attendu) {
                    problemes.add(exigence.getLibelle() + " : " + affectations.size()
                            + " affecte(s) sur " + attendu + " exige(s)");
                    continue;
                }

                for (var affectation : affectations) {
                    var engin = d.materielParId(affectation.getMaterielId());
                    if (engin.isEmpty()) {
                        problemes.add(exigence.getLibelle() + " : materiel affecte introuvable");
                    } else if (engin.get().getJustificatifChemin() == null) {
                        // Le DAO demande un justificatif de propriete ou de
                        // location : sans lui, le materiel ne compte pas.
                        problemes.add("justificatif manquant pour "
                                + engin.get().getDesignation());
                    }
                }
            }

            return problemes.isEmpty()
                    ? Verdict.conforme()
                    : Verdict.echec(String.join(" ; ", problemes) + ".", lien);
        }
    }
}
