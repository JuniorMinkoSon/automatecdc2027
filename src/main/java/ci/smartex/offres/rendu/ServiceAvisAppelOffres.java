package ci.smartex.offres.rendu;

import ci.smartex.offres.conformite.ControlesPublication;
import ci.smartex.offres.domain.AppelOffres;
import ci.smartex.offres.domain.Entreprise;
import ci.smartex.offres.domain.Lot;
import ci.smartex.offres.repo.DepotAppelOffres;
import ci.smartex.offres.repo.DepotEntreprise;
import ci.smartex.offres.repo.DepotLot;
import ci.smartex.offres.securite.ContexteOrganisation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Produit l'avis d'appel d'offres, cote autorite contractante.
 *
 * <p>C'est le document public : celui que les candidats lisent avant tout autre
 * et sur lequel ils reglent leur offre. Les durees et les montants qu'il
 * annonce viennent des memes champs que le reglement, ce qui lui interdit de le
 * contredire.
 */
@Service
public class ServiceAvisAppelOffres {

    private final ServiceJasper jasper;
    private final AssembleurPdf assembleur;
    private final DepotAppelOffres depotAppelOffres;
    private final DepotLot depotLot;
    private final DepotEntreprise depotEntreprise;

    public ServiceAvisAppelOffres(ServiceJasper jasper,
                                  AssembleurPdf assembleur,
                                  DepotAppelOffres depotAppelOffres,
                                  DepotLot depotLot,
                                  DepotEntreprise depotEntreprise) {
        this.jasper = jasper;
        this.assembleur = assembleur;
        this.depotAppelOffres = depotAppelOffres;
        this.depotLot = depotLot;
        this.depotEntreprise = depotEntreprise;
    }

    @Transactional(readOnly = true)
    public byte[] produire(UUID appelOffresId) {
        AppelOffres appel = depotAppelOffres.findById(appelOffresId).orElseThrow(
                () -> new IllegalArgumentException("Appel d'offres inconnu."));
        List<Lot> lots = depotLot.findAllByAppelOffresIdOrderByNumeroAsc(appelOffresId);

        Map<String, Object> parametres = new HashMap<>();
        parametres.put("MINISTERE", null);
        parametres.put("REGION", null);
        parametres.put("AUTORITE", appel.getAutoriteContractante());
        parametres.put("NUMERO_AVIS", appel.getNumeroAo());
        parametres.put("OBJET", appel.getIntitule());
        parametres.put("FINANCEMENT", appel.getFinancement());
        parametres.put("LIGNE_BUDGETAIRE", appel.getLigneBudgetaire());
        parametres.put("CLAUSES", TextesAvis.clauses(appel, lots));

        // L'en-tete institutionnel vient de la fiche de l'organisation qui
        // publie : une collectivite a un ministere de tutelle et une region,
        // qui ne se codent pas dans un gabarit.
        depotEntreprise.findByOrganisationId(ContexteOrganisation.exigerOrganisation())
                .ifPresent(fiche -> {
                    parametres.put("MINISTERE", fiche.getPiedDePage());
                    parametres.put("REGION", fiche.getAdresse());
                });

        List<LigneLot> lignes = lots.stream()
                .map(lot -> new LigneLot(
                        String.valueOf(lot.getNumero()),
                        lot.getIntitule() + (lot.getLocalite() == null || lot.getLocalite().isBlank()
                                ? "" : " — " + lot.getLocalite()),
                        lot.getDelaiMois() == null
                                ? "délai à fixer"
                                : lot.getDelaiMois() + " mois"))
                .toList();

        byte[] pdf = jasper.versPdf("rapports/avis_appel_offres.jrxml", parametres, lignes);

        // Tant que la publication est refusee, l'avis porte le filigrane : un
        // avis diffuse par erreur doit se reconnaitre au premier regard.
        var rapport = ControlesPublication.verifier(appel, lots);
        return rapport.publicationPossible() ? pdf : assembleur.filigraner(pdf);
    }

    @Transactional(readOnly = true)
    public ControlesPublication.Rapport controler(UUID appelOffresId) {
        AppelOffres appel = depotAppelOffres.findById(appelOffresId).orElseThrow(
                () -> new IllegalArgumentException("Appel d'offres inconnu."));
        return ControlesPublication.verifier(appel,
                depotLot.findAllByAppelOffresIdOrderByNumeroAsc(appelOffresId));
    }

    /** Ce que Jasper lit pour le tableau des lots de l'avis. */
    public record LigneLot(String numero, String designation, String delai) {

        public String getNumero() {
            return numero;
        }

        public String getDesignation() {
            return designation;
        }

        public String getDelai() {
            return delai;
        }
    }
}
