package ci.smartex.offres.rendu;

import ci.smartex.offres.conformite.MoteurConformite;
import ci.smartex.offres.conformite.RapportConformite;
import ci.smartex.offres.domain.*;
import ci.smartex.offres.repo.*;
import ci.smartex.offres.securite.ContexteOrganisation;
import ci.smartex.offres.service.ServiceJournal;
import ci.smartex.offres.service.ServiceStockage;
import ci.smartex.offres.util.MontantEnLettres;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Produit l'offre technique et l'offre financiere d'un dossier.
 *
 * <p>Toutes les pieces sortent de Jasper, a partir de la base. Il n'y a nulle
 * part dans ce service de texte recopie d'une piece a l'autre : l'intitule du
 * marche, qui etait recopie a la main dans au moins huit pieces et qui est la
 * premiere source d'erreurs, est ici un parametre lu une fois.
 *
 * <p>Le PDF est le seul livrable officiel. Le DOCX sert a relire un brouillon,
 * et les corrections se font dans la plateforme : c'est ce qui garantit zero
 * ecart entre la base et le dossier.
 */
@Service
public class ServiceGenerationDossier {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceGenerationDossier.class);
    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final ServiceJasper jasper;
    private final AssembleurPdf assembleur;
    private final ServiceStockage stockage;
    private final MoteurConformite moteur;
    private final ServiceJournal journal;

    private final DepotDossier depotDossier;
    private final DepotAppelOffres depotAppelOffres;
    private final DepotLot depotLot;
    private final DepotEntreprise depotEntreprise;
    private final DepotRepresentant depotRepresentant;
    private final DepotLigneDqe depotLigneDqe;
    private final DepotPieceAdmin depotPieceAdmin;
    private final DepotPersonnel depotPersonnel;
    private final DepotMateriel depotMateriel;
    private final DepotAffectationPersonnel depotAffectationPersonnel;
    private final DepotAffectationMateriel depotAffectationMateriel;
    private final DepotModele depotModele;
    private final DepotDocumentGenere depotDocumentGenere;
    private final DepotTachePlanning depotTachePlanning;

    public ServiceGenerationDossier(ServiceJasper jasper,
                                    AssembleurPdf assembleur,
                                    ServiceStockage stockage,
                                    MoteurConformite moteur,
                                    ServiceJournal journal,
                                    DepotDossier depotDossier,
                                    DepotAppelOffres depotAppelOffres,
                                    DepotLot depotLot,
                                    DepotEntreprise depotEntreprise,
                                    DepotRepresentant depotRepresentant,
                                    DepotLigneDqe depotLigneDqe,
                                    DepotPieceAdmin depotPieceAdmin,
                                    DepotPersonnel depotPersonnel,
                                    DepotMateriel depotMateriel,
                                    DepotAffectationPersonnel depotAffectationPersonnel,
                                    DepotAffectationMateriel depotAffectationMateriel,
                                    DepotModele depotModele,
                                    DepotDocumentGenere depotDocumentGenere,
                                    DepotTachePlanning depotTachePlanning) {
        this.jasper = jasper;
        this.assembleur = assembleur;
        this.stockage = stockage;
        this.moteur = moteur;
        this.journal = journal;
        this.depotDossier = depotDossier;
        this.depotAppelOffres = depotAppelOffres;
        this.depotLot = depotLot;
        this.depotEntreprise = depotEntreprise;
        this.depotRepresentant = depotRepresentant;
        this.depotLigneDqe = depotLigneDqe;
        this.depotPieceAdmin = depotPieceAdmin;
        this.depotPersonnel = depotPersonnel;
        this.depotMateriel = depotMateriel;
        this.depotAffectationPersonnel = depotAffectationPersonnel;
        this.depotAffectationMateriel = depotAffectationMateriel;
        this.depotModele = depotModele;
        this.depotDocumentGenere = depotDocumentGenere;
        this.depotTachePlanning = depotTachePlanning;
    }

    /**
     * Genere les deux offres d'un dossier.
     *
     * @param figer vrai au moment de la validation : le filigrane tombe et
     *              l'empreinte enregistree devient celle de la piece signee
     */
    @Transactional
    public List<DocumentGenere> genererTout(UUID dossierId, boolean figer) {
        Contexte contexte = rassembler(dossierId);
        List<DocumentGenere> produits = new ArrayList<>();

        produits.add(produire(contexte, DocumentGenere.Type.TECHNIQUE,
                offreTechnique(contexte), figer));
        produits.add(produire(contexte, DocumentGenere.Type.FINANCIERE,
                offreFinanciere(contexte), figer));
        produits.add(produire(contexte, DocumentGenere.Type.RAPPORT_CONFORMITE,
                rapportDeConformite(contexte), figer));

        journal.enregistrer(figer ? "DOCUMENTS_FIGES" : "BROUILLON_GENERE",
                "dossier", dossierId, produits.size() + " document(s)");
        return produits;
    }

    /**
     * Produit un document et le rend tout de suite, sans l'enregistrer.
     *
     * <p>C'est le chemin du telechargement : l'utilisateur veut son PDF, pas
     * une trace de plus en base. Les versions archivees, elles, passent par
     * {@link #genererTout} au moment de la validation - ce sont elles qui
     * portent une empreinte, parce qu'elles seules sont signees.
     *
     * <p>Le filigrane suit l'etat du dossier : tant qu'il n'est pas valide,
     * tout PDF qui en sort porte la mention BROUILLON, y compris celui-ci.
     */
    @Transactional(readOnly = true)
    public byte[] produireMaintenant(UUID dossierId, DocumentGenere.Type type) {
        Contexte contexte = rassembler(dossierId);

        byte[] pdf = switch (type) {
            case TECHNIQUE -> offreTechnique(contexte);
            case FINANCIERE -> offreFinanciere(contexte);
            case RAPPORT_CONFORMITE -> rapportDeConformite(contexte);
            case RECU -> throw new RenduImpossibleException(
                    "Un reçu ne se produit pas depuis un dossier.", null);
        };

        return contexte.dossier().porteFiligrane() ? assembleur.filigraner(pdf) : pdf;
    }

    /** L'export DOCX de relecture, jamais officiel et jamais fige. */
    @Transactional(readOnly = true)
    public byte[] relectureDocx(UUID dossierId, DocumentGenere.Type type) {
        Contexte contexte = rassembler(dossierId);
        Map<String, Object> parametres = parametres(contexte);

        if (type == DocumentGenere.Type.FINANCIERE) {
            parametres.put("MONTANT_LETTRES", montantEnLettres(contexte));
            parametres.put("MENTION_REGIME", mentionRegime(contexte));
            return jasper.versDocx("rapports/dqe.jrxml", parametres, lignesRendues(contexte));
        }
        parametres.put("TYPE_OFFRE", "OFFRE TECHNIQUE");
        return jasper.versDocx("rapports/page_garde.jrxml", parametres, List.of());
    }

    // ------------------------------------------------------------------ offres

    private byte[] offreTechnique(Contexte contexte) {
        List<byte[]> pieces = new ArrayList<>();

        Map<String, Object> garde = parametres(contexte);
        garde.put("TYPE_OFFRE", "OFFRE TECHNIQUE");
        pieces.add(jasper.versPdf("rapports/page_garde.jrxml", garde, List.of()));

        List<Modele> modelesTechniques = depotModele
                .findAllByOffreAndActifTrueOrderByOrdreSommaireAsc(Modele.Offre.TECHNIQUE);

        Map<String, Object> sommaire = parametres(contexte);
        sommaire.put("TYPE_OFFRE", "OFFRE TECHNIQUE");
        List<LigneSommaire> pieces_annoncees = new ArrayList<>();
        int rang = 1;
        for (Modele modele : modelesTechniques) {
            pieces_annoncees.add(new LigneSommaire(String.valueOf(rang++), modele.getLibelle()));
        }
        pieces.add(jasper.versPdf("rapports/sommaire.jrxml", sommaire, pieces_annoncees));

        // Les lettres types, dans l'ordre du sommaire.
        for (Modele modele : modelesTechniques) {

            if (modele.getFichierJrxml().endsWith("lettre_type.jrxml")) {
                Map<String, Object> parametres = parametres(contexte);
                parametres.put("TITRE", TextesLettres.titre(modele.getCode()));
                parametres.put("CORPS", TextesLettres.corps(modele.getCode(),
                        TextesLettres.Donnees.depuisParametres(
                                parametres,
                                contexte.dossier().getDelaiExecutionMois(),
                                contexte.dossier().getValiditeOffreJours(),
                                contexte.dossier().getValiditeGarantieJours(),
                                contexte.nombreDeLots() > 1)));
                pieces.add(jasper.versPdf(modele.getFichierJrxml(), parametres, List.of()));

            } else if ("PERSONNEL_CLE".equals(modele.getCode())) {
                pieces.add(formulairePersonnel(contexte, modele));

            } else if ("MATERIEL".equals(modele.getCode())) {
                pieces.add(formulaireMateriel(contexte, modele));

            } else if ("RENSEIGNEMENTS".equals(modele.getCode())) {
                pieces.add(jasper.versPdf(modele.getFichierJrxml(),
                        parametresRenseignements(contexte), List.of()));

            } else if ("ANTECEDENTS".equals(modele.getCode())) {
                pieces.add(formulaireAntecedents(contexte, modele));

            } else if ("PLANNING".equals(modele.getCode())) {
                pieces.add(formulairePlanning(contexte, modele));
            }
        }

        // Les pieces du coffre, telles qu'elles ont ete deposees.
        pieces.addAll(piecesDuCoffre(contexte));

        return assembleur.fusionner(pieces);
    }

    private byte[] offreFinanciere(Contexte contexte) {
        List<byte[]> pieces = new ArrayList<>();

        Map<String, Object> garde = parametres(contexte);
        garde.put("TYPE_OFFRE", "OFFRE FINANCIÈRE");
        pieces.add(jasper.versPdf("rapports/page_garde.jrxml", garde, List.of()));

        // Le sommaire est genere a partir des pieces reellement assemblees :
        // un sommaire tape a la main finit par annoncer une piece absente.
        Map<String, Object> sommaire = parametres(contexte);
        sommaire.put("TYPE_OFFRE", "OFFRE FINANCIÈRE");
        pieces.add(jasper.versPdf("rapports/sommaire.jrxml", sommaire, List.of(
                new LigneSommaire("1", "Lettre de soumission"),
                new LigneSommaire("2", "Détail quantitatif et estimatif"),
                new LigneSommaire("3", "Bordereau des prix unitaires"))));

        // Des qu'un marche est alloti, la commission doit retrouver d'un coup
        // d'oeil ou commence chaque lot dans un dossier de plusieurs centaines
        // de pages.
        if (contexte.nombreDeLots() > 1) {
            Map<String, Object> separateur = parametres(contexte);
            separateur.put("LOT_LOCALITE", contexte.lot()
                    .map(Lot::getLocalite).orElse(""));
            pieces.add(jasper.versPdf("rapports/separateur_lot.jrxml",
                    separateur, List.of()));
        }

        Map<String, Object> lettre = parametres(contexte);
        lettre.put("MONTANT_CHIFFRES", montantEnChiffres(contexte));
        lettre.put("MONTANT_LETTRES", montantEnLettres(contexte));
        lettre.put("DELAI_MOIS", contexte.dossier().getDelaiExecutionMois());
        lettre.put("VALIDITE_JOURS", contexte.dossier().getValiditeOffreJours());
        lettre.put("TIMBRE_FISCAL", contexte.appelOffres().getTimbreFiscal() == null
                ? "" : MontantEnLettres.chiffres(contexte.appelOffres().getTimbreFiscal()));
        pieces.add(jasper.versPdf("rapports/lettre_soumission.jrxml", lettre, List.of()));

        Map<String, Object> dqe = parametres(contexte);
        dqe.put("MONTANT_LETTRES", montantEnLettres(contexte));
        dqe.put("MENTION_REGIME", mentionRegime(contexte));
        pieces.add(jasper.versPdf("rapports/dqe.jrxml", dqe, lignesRendues(contexte)));

        // Le bordereau porte chaque prix deux fois, en chiffres et en lettres.
        // C'est la que la recopie manuelle se paie le plus cher, parce que la
        // commission retient la version en lettres quand les deux different.
        pieces.add(jasper.versPdf("rapports/bpu.jrxml", parametres(contexte),
                lignesDuBordereau(contexte)));

        return assembleur.fusionner(pieces);
    }

    /**
     * Les lignes du bordereau : ni quantite ni montant, c'est ce qui le
     * distingue du detail quantitatif et estimatif. Les melanger est une
     * non-conformite.
     */
    private List<LignePrix> lignesDuBordereau(Contexte contexte) {
        return contexte.lignes().stream()
                .map(ligne -> new LignePrix(
                        ligne.getSection(),
                        ligne.getNumeroPrix(),
                        ligne.getDesignation(),
                        ligne.getUnite(),
                        ligne.getPrixUnitaire(),
                        ligne.getPrixUnitaire() == null
                                ? "[prix à saisir]"
                                : MontantEnLettres.montant(ligne.getPrixUnitaire())))
                .toList();
    }

    private byte[] rapportDeConformite(Contexte contexte) {
        RapportConformite rapport = moteur.dernierRapport(contexte.dossier().getId());

        Map<String, Object> parametres = parametres(contexte);
        parametres.put("PREPARE_PAR", contexte.nomAuteur());
        parametres.put("VALIDE_PAR", contexte.nomValidateur());
        parametres.put("NB_EXECUTES", rapport.nombreExecutes());
        parametres.put("NB_BLOQUANTS", rapport.nombreBloquants());
        parametres.put("NB_AVERTISSEMENTS", rapport.nombreAvertissements());
        parametres.put("EMPREINTE", "");

        List<LigneControle> lignes = rapport.resultats().stream()
                .map(resultat -> new LigneControle(
                        resultat.getLibelle(),
                        resultat.getNiveau().name(),
                        resultat.isReussi() ? "OK" : "ÉCHEC",
                        resultat.getMessage() == null ? "" : resultat.getMessage()))
                .toList();

        return jasper.versPdf("rapports/rapport_conformite.jrxml", parametres, lignes);
    }

    private byte[] formulairePersonnel(Contexte contexte, Modele modele) {
        Map<String, Object> parametres = parametres(contexte);
        parametres.put("TITRE", modele.getLibelle());
        parametres.put("COLONNE_1", "Nom et prénoms");
        parametres.put("COLONNE_2", "Poste exigé");
        parametres.put("COLONNE_3", "Diplôme");
        parametres.put("COLONNE_4", "Expérience");

        List<LigneMoyen> lignes = contexte.affectationsPersonnel().stream()
                .map(affectation -> {
                    Personnel personne = contexte.personnelParId(affectation.getPersonnelId());
                    if (personne == null) {
                        return new LigneMoyen("(introuvable)", affectation.getPosteExige(), "", "");
                    }
                    return new LigneMoyen(
                            personne.getNom(),
                            affectation.getPosteExige(),
                            personne.getDiplome() == null ? "" : personne.getDiplome(),
                            personne.getAnneesExperience() + " ans");
                })
                .toList();

        return jasper.versPdf(modele.getFichierJrxml(), parametres, lignes);
    }

    private byte[] formulaireMateriel(Contexte contexte, Modele modele) {
        Map<String, Object> parametres = parametres(contexte);
        parametres.put("TITRE", modele.getLibelle());
        parametres.put("COLONNE_1", "Désignation");
        parametres.put("COLONNE_2", "Marque et capacité");
        parametres.put("COLONNE_3", "Année");
        parametres.put("COLONNE_4", "Provenance");

        List<LigneMoyen> lignes = contexte.affectationsMateriel().stream()
                .map(affectation -> {
                    Materiel engin = contexte.materielParId(affectation.getMaterielId());
                    if (engin == null) {
                        return new LigneMoyen(affectation.getDesignationExigee(),
                                "(introuvable)", "", "");
                    }
                    return new LigneMoyen(
                            engin.getDesignation(),
                            String.join(" ",
                                    engin.getMarque() == null ? "" : engin.getMarque(),
                                    engin.getCapacite() == null ? "" : engin.getCapacite()).trim(),
                            engin.getAnnee() == null ? "" : String.valueOf(engin.getAnnee()),
                            engin.getProvenance().name().toLowerCase());
                })
                .toList();

        return jasper.versPdf(modele.getFichierJrxml(), parametres, lignes);
    }

    /** Le formulaire de renseignements, entierement tire de la fiche entreprise. */
    private Map<String, Object> parametresRenseignements(Contexte contexte) {
        Map<String, Object> parametres = parametres(contexte);
        Entreprise entreprise = contexte.entreprise();
        parametres.put("ENTREPRISE_EMAIL", entreprise.getEmail());
        parametres.put("ENTREPRISE_REGIME", entreprise.getRegimeFiscal());
        parametres.put("ENTREPRISE_ANNEE", entreprise.getAnneeEnregistrement() == null
                ? null : String.valueOf(entreprise.getAnneeEnregistrement()));
        parametres.put("PAYS", entreprise.getPays());
        // Le groupement n'est pas modelise : l'absence se declare « Néant »
        // plutot que de laisser un blanc qui se lit comme un oubli.
        parametres.put("GROUPEMENT", null);
        return parametres;
    }

    /**
     * Le formulaire des antecedents n'imprime qu'une des deux declarations.
     * Les deux ensemble laisseraient croire qu'une case a ete cochee par
     * inadvertance.
     */
    private byte[] formulaireAntecedents(Contexte contexte, Modele modele) {
        Map<String, Object> parametres = parametres(contexte);
        parametres.put("AUCUN_ANTECEDENT", contexte.dossier().isSansAntecedent());
        parametres.put("DATE_REFERENCE", null);

        List<LigneAntecedent> lignes = contexte.dossier().isSansAntecedent()
                ? List.of()
                : List.of(new LigneAntecedent("", "",
                        contexte.dossier().getAntecedentsDetail() == null
                                ? "" : contexte.dossier().getAntecedentsDetail(), ""));

        return jasper.versPdf(modele.getFichierJrxml(), parametres, lignes);
    }

    private byte[] formulairePlanning(Contexte contexte, Modele modele) {
        Map<String, Object> parametres = parametres(contexte);
        Integer delai = contexte.dossier().getDelaiExecutionMois();
        parametres.put("DELAI_MOIS", delai);
        parametres.put("DELAI_LETTRES", delai == null
                ? null : MontantEnLettres.nombre(delai));

        List<LignePlanning> lignes = contexte.taches().stream()
                .map(tache -> new LignePlanning(tache.getLibelle(),
                        tache.getDebutMois(), tache.getDureeMois()))
                .toList();

        return jasper.versPdf(modele.getFichierJrxml(), parametres, lignes);
    }

    /**
     * Les pieces du coffre, dechiffrees le temps de l'assemblage.
     *
     * <p>Une piece illisible ne fait pas echouer toute la generation : elle est
     * signalee et le reste du dossier se construit. Un brouillon incomplet
     * qu'on peut regarder vaut mieux qu'une erreur devant un ecran vide.
     */
    private List<byte[]> piecesDuCoffre(Contexte contexte) {
        List<byte[]> pieces = new ArrayList<>();
        for (PieceAdmin piece : contexte.pieces()) {
            if (!piece.valableLe(contexte.dateReference())) {
                continue;
            }
            try {
                pieces.add(stockage.lire(piece.getFichierChemin()));
            } catch (IOException | RuntimeException erreur) {
                LOG.warn("Piece {} illisible, ecartee de l'assemblage : {}",
                        piece.getType(), erreur.getMessage());
            }
        }
        return pieces;
    }

    // ------------------------------------------------------------------ parametres

    private Map<String, Object> parametres(Contexte contexte) {
        Entreprise entreprise = contexte.entreprise();
        AppelOffres appelOffres = contexte.appelOffres();

        Map<String, Object> parametres = new HashMap<>();
        parametres.put("ENTREPRISE_RAISON_SOCIALE", entreprise.getRaisonSociale());
        parametres.put("ENTREPRISE_SIGLE", entreprise.getSigle());
        parametres.put("ENTREPRISE_RCCM", entreprise.getRccm());
        parametres.put("ENTREPRISE_CC", entreprise.getCompteContribuable());
        parametres.put("ENTREPRISE_ADRESSE", entreprise.getAdresse());
        parametres.put("ENTREPRISE_BP", entreprise.getBoitePostale());
        parametres.put("ENTREPRISE_TELEPHONES", entreprise.getTelephones());

        parametres.put("AO_NUMERO", appelOffres.getNumeroAo());
        parametres.put("AO_INTITULE", appelOffres.getIntitule());
        parametres.put("AO_AUTORITE", appelOffres.getAutoriteContractante());
        parametres.put("AO_DESTINATAIRE", appelOffres.getDestinataire());
        parametres.put("LOT_LIBELLE", contexte.libelleLot());

        parametres.put("LIEU", lieu(entreprise));
        parametres.put("DATE_LETTRES", MontantEnLettres.date(LocalDate.now(ABIDJAN)));
        parametres.put("SIGNATAIRE_NOM", contexte.nomSignataire());
        parametres.put("SIGNATAIRE_FONCTION", contexte.fonctionSignataire());

        return parametres;
    }

    private String montantEnChiffres(Contexte contexte) {
        Long total = contexte.dossier().getMontantSoumission();
        return total == null ? "—" : MontantEnLettres.chiffres(total);
    }

    private String montantEnLettres(Contexte contexte) {
        Long total = contexte.dossier().getMontantSoumission();
        if (total == null) {
            // Le dossier n'est pas chiffre : le dire est plus honnete que
            // d'imprimer un zero en toutes lettres.
            return "montant non arrêté, dossier incomplet";
        }
        return MontantEnLettres.montant(total, mentionRegime(contexte));
    }

    private String mentionRegime(Contexte contexte) {
        Boolean tva = contexte.appelOffres().getTvaApplicable();
        if (tva == null) {
            return "";
        }
        return tva ? "toutes taxes comprises" : "hors taxes";
    }

    private String lieu(Entreprise entreprise) {
        if (entreprise.getAdresse() == null || entreprise.getAdresse().isBlank()) {
            return "Abidjan";
        }
        return entreprise.getAdresse().split(",")[0].trim();
    }

    private List<LigneRendue> lignesRendues(Contexte contexte) {
        return contexte.lignes().stream()
                .map(ligne -> new LigneRendue(
                        ligne.getSection(),
                        ligne.getNumeroPrix(),
                        ligne.getDesignation(),
                        ligne.getUnite(),
                        ligne.getQuantite(),
                        ligne.getPrixUnitaire(),
                        ligne.getMontant()))
                .toList();
    }

    // ------------------------------------------------------------------ production

    private DocumentGenere produire(Contexte contexte,
                                    DocumentGenere.Type type,
                                    byte[] pdf,
                                    boolean figer) {
        byte[] final_ = figer ? pdf : assembleur.filigraner(pdf);
        String empreinte = ServiceStockage.empreinte(final_);

        String chemin;
        try {
            chemin = stockage.deposer("sorties",
                    type.name().toLowerCase() + "-v" + contexte.dossier().getVersion() + ".pdf",
                    final_);
        } catch (IOException erreur) {
            throw new RenduImpossibleException("Enregistrement du PDF impossible.", erreur);
        }

        DocumentGenere document = new DocumentGenere();
        document.setOrganisationId(ContexteOrganisation.exigerOrganisation());
        document.setDossierId(contexte.dossier().getId());
        document.setType(type);
        document.setFormat(DocumentGenere.Format.PDF);
        document.setVersion(contexte.dossier().getVersion());
        document.setBrouillon(!figer);
        document.setEmpreinteSha256(empreinte);
        document.setChemin(chemin);
        document.setTailleOctets((long) final_.length);

        return depotDocumentGenere.save(document);
    }

    private Contexte rassembler(UUID dossierId) {
        Dossier dossier = depotDossier.findById(dossierId).orElseThrow(
                () -> new IllegalArgumentException("Dossier inconnu."));
        UUID organisation = ContexteOrganisation.exigerOrganisation();

        AppelOffres appelOffres = depotAppelOffres.findById(dossier.getAppelOffresId())
                .orElseThrow(() -> new IllegalStateException("Appel d'offres introuvable."));

        Entreprise entreprise = depotEntreprise.findByOrganisationId(organisation)
                .orElseThrow(() -> new IllegalStateException(
                        "La fiche entreprise doit etre remplie avant toute generation."));

        Optional<Lot> lot = Optional.ofNullable(dossier.getLotId()).flatMap(depotLot::findById);

        Optional<Representant> signataire = Optional
                .ofNullable(dossier.getRepresentantId())
                .flatMap(depotRepresentant::findById)
                .or(() -> depotRepresentant
                        .findFirstByOrganisationIdAndSignataireDefautTrue(organisation));

        LocalDate dateReference = appelOffres.getDateLimiteDepot() == null
                ? LocalDate.now(ABIDJAN)
                : appelOffres.getDateLimiteDepot().atZone(ABIDJAN).toLocalDate();

        return new Contexte(
                dossier,
                appelOffres,
                lot,
                entreprise,
                signataire,
                depotLigneDqe.findAllByDossierIdOrderByOrdreSectionAscOrdreAsc(dossierId),
                depotPieceAdmin.findAllByOrganisationId(organisation),
                depotPersonnel.findAllByOrganisationId(organisation),
                depotMateriel.findAllByOrganisationId(organisation),
                depotAffectationPersonnel.findAllByDossierId(dossierId),
                depotAffectationMateriel.findAllByDossierId(dossierId),
                depotTachePlanning.findAllByDossierIdOrderByOrdreAsc(dossierId),
                depotLot.findAllByAppelOffresIdOrderByNumeroAsc(appelOffres.getId()).size(),
                dateReference);
    }

    // ------------------------------------------------------------------ types

    private record Contexte(Dossier dossier,
                            AppelOffres appelOffres,
                            Optional<Lot> lot,
                            Entreprise entreprise,
                            Optional<Representant> signataire,
                            List<LigneDqe> lignes,
                            List<PieceAdmin> pieces,
                            List<Personnel> personnel,
                            List<Materiel> materiel,
                            List<AffectationPersonnel> affectationsPersonnel,
                            List<AffectationMateriel> affectationsMateriel,
                            List<TachePlanning> taches,
                            int nombreDeLots,
                            LocalDate dateReference) {

        String libelleLot() {
            return lot.map(l -> "Lot " + l.getNumero()
                            + (l.getIntitule() == null ? "" : " — " + l.getIntitule()))
                    .orElse("");
        }

        String nomSignataire() {
            return signataire.map(Representant::getNom).orElse("");
        }

        String fonctionSignataire() {
            return signataire.map(Representant::getFonction).orElse("");
        }

        String nomAuteur() {
            return dossier.getAuteurId() == null ? "" : dossier.getAuteurId().toString();
        }

        String nomValidateur() {
            return dossier.getValidateurId() == null ? "" : dossier.getValidateurId().toString();
        }

        Personnel personnelParId(UUID id) {
            return personnel.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
        }

        Materiel materielParId(UUID id) {
            return materiel.stream().filter(m -> m.getId().equals(id)).findFirst().orElse(null);
        }
    }

    /** Ce que Jasper lit pour le DQE. */
    public record LigneRendue(String section, String numeroPrix, String designation,
                              String unite, java.math.BigDecimal quantite,
                              Long prixUnitaire, Long montant) {

        public String getSection() {
            return section;
        }

        public String getNumeroPrix() {
            return numeroPrix;
        }

        public String getDesignation() {
            return designation;
        }

        public String getUnite() {
            return unite;
        }

        public java.math.BigDecimal getQuantite() {
            return quantite;
        }

        public Long getPrixUnitaire() {
            return prixUnitaire;
        }

        public Long getMontant() {
            return montant;
        }
    }

    /** Ce que Jasper lit pour les formulaires de moyens. */
    public record LigneMoyen(String valeur1, String valeur2, String valeur3, String valeur4) {

        public String getValeur1() {
            return valeur1;
        }

        public String getValeur2() {
            return valeur2;
        }

        public String getValeur3() {
            return valeur3;
        }

        public String getValeur4() {
            return valeur4;
        }
    }

    /** Ce que Jasper lit pour les antecedents. */
    public record LigneAntecedent(String annee, String fraction, String contrat,
                                  String montant) {

        public String getAnnee() {
            return annee;
        }

        public String getFraction() {
            return fraction;
        }

        public String getContrat() {
            return contrat;
        }

        public String getMontant() {
            return montant;
        }
    }

    /** Ce que Jasper lit pour le planning. */
    public record LignePlanning(String tache, Integer debutMois, Integer dureeMois) {

        public String getTache() {
            return tache;
        }

        public Integer getDebutMois() {
            return debutMois;
        }

        public Integer getDureeMois() {
            return dureeMois;
        }
    }

    /** Ce que Jasper lit pour le sommaire. */
    public record LigneSommaire(String numero, String intitule) {

        public String getNumero() {
            return numero;
        }

        public String getIntitule() {
            return intitule;
        }
    }

    /** Ce que Jasper lit pour le bordereau des prix unitaires. */
    public record LignePrix(String section, String numeroPrix, String designation,
                            String unite, Long prixUnitaire, String prixEnLettres) {

        public String getSection() {
            return section;
        }

        public String getNumeroPrix() {
            return numeroPrix;
        }

        public String getDesignation() {
            return designation;
        }

        public String getUnite() {
            return unite;
        }

        public Long getPrixUnitaire() {
            return prixUnitaire;
        }

        public String getPrixEnLettres() {
            return prixEnLettres;
        }
    }

    /** Ce que Jasper lit pour le rapport de conformite. */
    public record LigneControle(String libelle, String niveau, String etat, String message) {

        public String getLibelle() {
            return libelle;
        }

        public String getNiveau() {
            return niveau;
        }

        public String getEtat() {
            return etat;
        }

        public String getMessage() {
            return message;
        }
    }
}
