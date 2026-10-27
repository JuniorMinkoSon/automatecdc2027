package ci.smartex.offres.rendu;

import ci.smartex.offres.domain.AppelOffres;
import ci.smartex.offres.domain.Entreprise;
import ci.smartex.offres.domain.Lot;
import ci.smartex.offres.domain.SectionDao;
import ci.smartex.offres.repo.DepotAppelOffres;
import ci.smartex.offres.repo.DepotEntreprise;
import ci.smartex.offres.repo.DepotLot;
import ci.smartex.offres.repo.DepotSectionDao;
import ci.smartex.offres.securite.ContexteOrganisation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assemble le dossier d'appel d'offres complet, cote autorite contractante.
 *
 * <p>Page de garde, avis, puis les sections dans l'ordre. L'objet du marche n'y
 * figure qu'une fois en base : dans un dossier redige a la main il est recopie
 * dans l'avis, dans les donnees particulieres, dans le cahier technique et dans
 * chaque formulaire, et c'est le fragment qui concentre les divergences.
 *
 * <p>Les sections figees par le code des marches - Instructions aux candidats,
 * Cahier des clauses administratives generales - sont creees verrouillees. Une
 * collectivite qui y glisserait une clause produirait un dossier attaquable, et
 * c'est elle qui en repondrait.
 */
@Service
public class ServiceDossierAppelOffres {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    /**
     * La charpente d'un dossier, dans l'ordre du code des marches.
     *
     * <p>{@code figee} marque les sections dont le texte ne se modifie pas.
     * Elles sont livrees avec la plateforme ; les autres se redigent, et c'est
     * la que le temps se perd aujourd'hui.
     */
    private static final List<Charpente> CHARPENTE = List.of(
            new Charpente("AVIS", "0", "Avis d'appel d'offres", false),
            new Charpente("IC", "I", "Instructions aux candidats", true),
            new Charpente("DPAO", "II", "Données particulières de l'appel d'offres", false),
            new Charpente("CRITERES", "III", "Critères d'évaluation et de qualification", false),
            new Charpente("FORMULAIRES", "IV", "Formulaires de soumission", true),
            new Charpente("CCTP", "V", "Cahier des clauses techniques et plans", false),
            new Charpente("CCAG", "VI", "Cahier des clauses administratives générales", true),
            new Charpente("CCAP", "VII", "Cahier des clauses administratives particulières", false),
            new Charpente("FORMULAIRES_MARCHE", "VIII", "Formulaires du marché", true));

    private final ServiceJasper jasper;
    private final AssembleurPdf assembleur;
    private final ServiceAvisAppelOffres serviceAvis;
    private final DepotAppelOffres depotAppelOffres;
    private final DepotLot depotLot;
    private final DepotSectionDao depotSection;
    private final DepotEntreprise depotEntreprise;

    public ServiceDossierAppelOffres(ServiceJasper jasper,
                                     AssembleurPdf assembleur,
                                     ServiceAvisAppelOffres serviceAvis,
                                     DepotAppelOffres depotAppelOffres,
                                     DepotLot depotLot,
                                     DepotSectionDao depotSection,
                                     DepotEntreprise depotEntreprise) {
        this.jasper = jasper;
        this.assembleur = assembleur;
        this.serviceAvis = serviceAvis;
        this.depotAppelOffres = depotAppelOffres;
        this.depotLot = depotLot;
        this.depotSection = depotSection;
        this.depotEntreprise = depotEntreprise;
    }

    /**
     * Cree la charpente d'un dossier si elle n'existe pas encore. Les sections
     * deja redigees ne sont jamais touchees : relancer la creation apres une
     * modification du marche ne doit pas effacer du travail.
     */
    @Transactional
    public List<SectionDao> preparerLesSections(UUID appelOffresId) {
        UUID organisation = ContexteOrganisation.exigerOrganisation();
        List<SectionDao> existantes = depotSection
                .findAllByAppelOffresIdOrderByOrdreAsc(appelOffresId);

        int ordre = 0;
        for (Charpente modele : CHARPENTE) {
            ordre++;
            boolean dejaLa = existantes.stream()
                    .anyMatch(section -> section.getCode().equals(modele.code()));
            if (dejaLa) {
                continue;
            }

            SectionDao section = new SectionDao();
            section.setOrganisationId(organisation);
            section.setAppelOffresId(appelOffresId);
            section.setCode(modele.code());
            section.setIntitule(modele.intitule());
            section.setOrdre(ordre);
            section.setModifiable(!modele.figee());
            section.setContenu(modele.figee() ? TextesReglementaires.texte(modele.code()) : null);
            depotSection.save(section);
        }

        return depotSection.findAllByAppelOffresIdOrderByOrdreAsc(appelOffresId);
    }

    /** Le dossier complet, en un seul PDF. */
    @Transactional(readOnly = true)
    public byte[] produire(UUID appelOffresId) {
        AppelOffres appel = depotAppelOffres.findById(appelOffresId).orElseThrow(
                () -> new IllegalArgumentException("Appel d'offres inconnu."));
        List<Lot> lots = depotLot.findAllByAppelOffresIdOrderByNumeroAsc(appelOffresId);
        List<SectionDao> sections = depotSection
                .findAllByAppelOffresIdOrderByOrdreAsc(appelOffresId);

        Map<String, Object> commun = parametres(appel);
        List<byte[]> pieces = new ArrayList<>();

        pieces.add(jasper.versPdf("rapports/page_garde_dao.jrxml", commun, List.of()));

        // Le sommaire annonce les sections reellement presentes, pas celles
        // que la charpente prevoit.
        Map<String, Object> sommaire = new HashMap<>(commun);
        sommaire.put("TYPE_OFFRE", "DOSSIER D'APPEL D'OFFRES");
        sommaire.put("ENTREPRISE_RAISON_SOCIALE", appel.getAutoriteContractante());
        sommaire.put("ENTREPRISE_RCCM", null);
        sommaire.put("LOT_LIBELLE", null);
        pieces.add(jasper.versPdf("rapports/sommaire.jrxml", sommaire,
                numeroterLesSections(sections)));

        for (SectionDao section : sections) {
            if ("AVIS".equals(section.getCode())) {
                pieces.add(serviceAvis.produire(appelOffresId));
                continue;
            }
            Map<String, Object> parametres = new HashMap<>(commun);
            parametres.put("SECTION_NUMERO", numeroRomain(section.getCode()));
            parametres.put("SECTION_INTITULE", section.getIntitule());
            parametres.put("SECTION_CONTENU", section.getContenu());
            parametres.put("FIGEE", !section.isModifiable());
            pieces.add(jasper.versPdf("rapports/section_dao.jrxml", parametres, List.of()));
        }

        byte[] dossier = assembleur.fusionner(pieces);

        // Tant que la publication est refusee, le dossier porte le filigrane :
        // un dossier diffuse par erreur doit se reconnaitre au premier regard.
        return serviceAvis.controler(appelOffresId).publicationPossible()
                ? dossier
                : assembleur.filigraner(dossier);
    }

    // ------------------------------------------------------------------ interne

    private Map<String, Object> parametres(AppelOffres appel) {
        Map<String, Object> parametres = new HashMap<>();
        parametres.put("AUTORITE", appel.getAutoriteContractante());
        parametres.put("NUMERO_AVIS", appel.getNumeroAo());
        parametres.put("OBJET", appel.getIntitule());
        parametres.put("FINANCEMENT", appel.getFinancement());
        parametres.put("LIGNE_BUDGETAIRE", appel.getLigneBudgetaire());
        parametres.put("ANNEE", String.valueOf(LocalDate.now(ABIDJAN).getYear()));
        parametres.put("MINISTERE", null);
        parametres.put("REGION", null);

        depotEntreprise.findByOrganisationId(ContexteOrganisation.exigerOrganisation())
                .ifPresent(fiche -> {
                    parametres.put("MINISTERE", fiche.getPiedDePage());
                    parametres.put("REGION", fiche.getAdresse());
                });
        return parametres;
    }

    private List<ServiceGenerationDossier.LigneSommaire> numeroterLesSections(
            List<SectionDao> sections) {
        List<ServiceGenerationDossier.LigneSommaire> lignes = new ArrayList<>();
        for (SectionDao section : sections) {
            String numero = numeroRomain(section.getCode());
            lignes.add(new ServiceGenerationDossier.LigneSommaire(
                    numero.isEmpty() ? "0" : numero,
                    section.getIntitule() + (section.estVide() ? "  [à rédiger]" : "")));
        }
        return lignes;
    }

    private String numeroRomain(String code) {
        return CHARPENTE.stream()
                .filter(charpente -> charpente.code().equals(code))
                .map(Charpente::numero)
                .findFirst()
                .orElse("");
    }

    private record Charpente(String code, String numero, String intitule, boolean figee) {
    }
}
