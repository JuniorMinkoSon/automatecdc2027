package ci.smartex.offres.jobs;

import ci.smartex.offres.config.ProprietesApp;
import ci.smartex.offres.domain.AppelOffres;
import ci.smartex.offres.domain.Exigence;
import ci.smartex.offres.domain.Lot;
import ci.smartex.offres.extraction.*;
import ci.smartex.offres.repo.DepotAppelOffres;
import ci.smartex.offres.repo.DepotExigence;
import ci.smartex.offres.repo.DepotLot;
import ci.smartex.offres.securite.ContexteOrganisation;
import ci.smartex.offres.service.ServiceJournal;
import ci.smartex.offres.service.ServiceStockage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jobrunr.jobs.annotations.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * WF1 — Analyse d'un DAO.
 *
 * <p>Conversion en PDF si besoin, OCR si le document est scanne, extraction par
 * le modele, puis ecriture avec le statut « A verifier ». Jamais « Verifie » :
 * c'est un humain qui le passe a verifie, apres avoir relu les champs a cote de
 * leur source.
 *
 * <p>Toute panne laisse l'appel d'offres en echec d'analyse avec son message,
 * et le DAO televerse reste la. L'utilisateur saisit alors les champs a la
 * main : l'IA accelere le travail, elle n'en est pas la condition.
 */
@Component
public class TacheAnalyseDao {

    private static final Logger LOG = LoggerFactory.getLogger(TacheAnalyseDao.class);
    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final ServiceStockage stockage;
    private final ClientGotenberg gotenberg;
    private final ClientOcr ocr;
    private final ExtracteurDao extracteur;
    private final DepotAppelOffres depotAppelOffres;
    private final DepotLot depotLot;
    private final DepotExigence depotExigence;
    private final ServiceJournal journal;
    private final ProprietesApp proprietes;
    private final ObjectMapper json = new ObjectMapper();

    public TacheAnalyseDao(ServiceStockage stockage,
                           ClientGotenberg gotenberg,
                           ClientOcr ocr,
                           ExtracteurDao extracteur,
                           DepotAppelOffres depotAppelOffres,
                           DepotLot depotLot,
                           DepotExigence depotExigence,
                           ServiceJournal journal,
                           ProprietesApp proprietes) {
        this.stockage = stockage;
        this.gotenberg = gotenberg;
        this.ocr = ocr;
        this.extracteur = extracteur;
        this.depotAppelOffres = depotAppelOffres;
        this.depotLot = depotLot;
        this.depotExigence = depotExigence;
        this.journal = journal;
        this.proprietes = proprietes;
    }

    /**
     * @param organisationId pose sur le fil d'execution avant toute requete :
     *                       une tache de fond n'a pas de session, donc pas
     *                       d'organisation, et sans cela la base ne lui
     *                       renverrait rien
     */
    @Job(name = "Analyse du DAO %1", retries = 2)
    public void executer(UUID organisationId, UUID appelOffresId, String nomFichier) {
        ContexteOrganisation.dans(organisationId, () -> analyser(appelOffresId, nomFichier));
    }

    @Transactional
    protected void analyser(UUID appelOffresId, String nomFichier) {
        AppelOffres appelOffres = depotAppelOffres.findById(appelOffresId).orElseThrow(
                () -> new IllegalArgumentException("Appel d'offres inconnu : " + appelOffresId));

        try {
            byte[] source = stockage.lire(appelOffres.getDaoChemin());
            byte[] pdf = estDejaPdf(nomFichier) ? source : gotenberg.versPdf(nomFichier, source);
            String texte = ocr.texte(pdf);

            if (!proprietes.getIa().isActive()) {
                appelOffres.setStatut(AppelOffres.Statut.A_VERIFIER);
                appelOffres.setErreurAnalyse(
                        "L'extraction automatique est desactivee. Saisissez les champs a la main.");
                depotAppelOffres.save(appelOffres);
                return;
            }

            ExtractionDao extraction = extracteur.extraire(texte);
            appliquer(appelOffres, extraction);

            journal.enregistrer("DAO_ANALYSE", "appel_offres", appelOffresId,
                    extraction.lots.size() + " lot(s), " + extraction.exigences.size()
                            + " exigence(s)");

        } catch (Exception erreur) {
            LOG.error("Analyse du DAO {} impossible.", appelOffresId, erreur);
            appelOffres.setStatut(AppelOffres.Statut.ECHEC_ANALYSE);
            appelOffres.setErreurAnalyse(erreur.getMessage());
            depotAppelOffres.save(appelOffres);
            journal.enregistrer("DAO_ANALYSE_ECHEC", "appel_offres", appelOffresId,
                    erreur.getMessage());
        }
    }

    private void appliquer(AppelOffres appelOffres, ExtractionDao extraction) {
        UUID organisation = ContexteOrganisation.exigerOrganisation();

        if (!extraction.intitule.isBlank()) {
            appelOffres.setIntitule(extraction.intitule);
        }
        appelOffres.setNumeroAo(vide(extraction.numeroAo));
        appelOffres.setAutoriteContractante(vide(extraction.autoriteContractante));
        appelOffres.setMaitreOuvrage(vide(extraction.maitreOuvrage));
        appelOffres.setMaitreOeuvre(vide(extraction.maitreOeuvre));
        appelOffres.setDestinataire(vide(extraction.destinataire));
        appelOffres.setFinancement(vide(extraction.financement));
        appelOffres.setRegimePrix(vide(extraction.regimePrix));
        appelOffres.setValiditeOffreJours(entier(extraction.validiteOffreJours));
        appelOffres.setTimbreFiscal(montant(extraction.timbreFiscal));
        appelOffres.setTvaApplicable(ouiNon(extraction.tvaApplicable));
        appelOffres.setDateLimiteDepot(horodatage(extraction.dateLimiteDepot));

        try {
            appelOffres.setExtractionJson(json.writeValueAsString(extraction));
        } catch (Exception erreur) {
            LOG.warn("Extraction non serialisable, la trace brute sera absente.", erreur);
        }

        // Le statut dit ce qu'il en est : l'extraction est faite, la relecture
        // ne l'est pas. Aucune piece ne sortira de ces donnees avant.
        appelOffres.setStatut(AppelOffres.Statut.A_VERIFIER);
        appelOffres.setErreurAnalyse(extraction.pointsDAttention.isEmpty()
                ? null
                : String.join(" · ", extraction.pointsDAttention));
        depotAppelOffres.save(appelOffres);

        // Relance : une nouvelle analyse remplace l'ancienne plutot que de
        // s'ajouter a elle.
        depotExigence.deleteAllByAppelOffresId(appelOffres.getId());
        depotLot.deleteAllByAppelOffresId(appelOffres.getId());
        depotLot.flush();

        List<Lot> lots = new ArrayList<>();
        for (ExtractionDao.LotExtrait extrait : extraction.lots) {
            Integer numero = entier(extrait.numero);
            if (numero == null) {
                continue;
            }
            Lot lot = new Lot();
            lot.setOrganisationId(organisation);
            lot.setAppelOffresId(appelOffres.getId());
            lot.setNumero(numero);
            lot.setIntitule(extrait.intitule.isBlank() ? "Lot " + numero : extrait.intitule);
            lot.setLocalite(vide(extrait.localite));
            lot.setDelaiMois(entier(extrait.delaiMois));
            lot.setMontantGarantie(montant(extrait.montantGarantie));
            lots.add(depotLot.save(lot));
        }

        for (ExtractionDao.ExigenceExtraite extraite : extraction.exigences) {
            if (extraite.libelle.isBlank()) {
                continue;
            }
            Exigence exigence = new Exigence();
            exigence.setOrganisationId(organisation);
            exigence.setAppelOffresId(appelOffres.getId());
            exigence.setType(typeExigence(extraite.type));
            exigence.setLibelle(extraite.libelle);
            exigence.setValeur(vide(extraite.valeur));
            exigence.setQuantiteMin(entier(extraite.quantiteMin));
            exigence.setAnneesMin(entier(extraite.anneesMin));
            exigence.setEliminatoire("oui".equalsIgnoreCase(extraite.eliminatoire.trim()));
            exigence.setSourceDao(vide(extraite.sourceDao));

            Integer numeroLot = entier(extraite.numeroLot);
            if (numeroLot != null) {
                lots.stream()
                        .filter(lot -> lot.getNumero() == numeroLot)
                        .findFirst()
                        .ifPresent(lot -> exigence.setLotId(lot.getId()));
            }
            depotExigence.save(exigence);
        }
    }

    // ------------------------------------------------------------------ conversions

    /**
     * Toutes les conversions suivent la meme regle : ce qui ne se lit pas
     * devient nul, jamais une valeur par defaut. Un champ vide se voit a
     * l'ecran et se corrige ; un zero silencieux part au depot.
     */
    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static Integer entier(String valeur) {
        String propre = valeur == null ? "" : valeur.replaceAll("[^0-9]", "");
        if (propre.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(propre);
        } catch (NumberFormatException erreur) {
            return null;
        }
    }

    private static Long montant(String valeur) {
        String propre = valeur == null ? "" : valeur.replaceAll("[^0-9]", "");
        if (propre.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(propre);
        } catch (NumberFormatException erreur) {
            return null;
        }
    }

    private static Boolean ouiNon(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            // Le DAO ne tranche pas : on ne tranche pas non plus, et le
            // controle REGIME_PRIX posera la question au validateur.
            return null;
        }
        return "oui".equalsIgnoreCase(valeur.trim());
    }

    private static Instant horodatage(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(valeur.trim()).atZone(ABIDJAN).toInstant();
        } catch (Exception erreur) {
            try {
                return java.time.LocalDate.parse(valeur.trim())
                        .atTime(23, 59).atZone(ABIDJAN).toInstant();
            } catch (Exception ignore) {
                return null;
            }
        }
    }

    private static Exigence.Type typeExigence(String valeur) {
        try {
            return Exigence.Type.valueOf(valeur.trim().toUpperCase());
        } catch (Exception erreur) {
            return Exigence.Type.AUTRE;
        }
    }

    private static boolean estDejaPdf(String nomFichier) {
        return nomFichier != null && nomFichier.toLowerCase().endsWith(".pdf");
    }
}
