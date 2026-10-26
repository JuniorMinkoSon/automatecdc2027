-- Schema de la plateforme de generation automatique des offres.
-- Regles structurantes :
--   * tout montant est un entier de F CFA (pas de decimales, donc pas d'arrondi) ;
--   * le montant d'une ligne de DQE n'est jamais saisi, il est calcule par la base ;
--   * toute table metier porte organisation_id, qui porte l'isolation (voir V2).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------- comptes

CREATE TABLE organisation (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nom                 text        NOT NULL,
    statut              text        NOT NULL DEFAULT 'EN_ATTENTE'
                        CHECK (statut IN ('EN_ATTENTE','ACTIVE','LECTURE_SEULE','SUSPENDUE')),
    mode                text        NOT NULL DEFAULT 'LIBRE_SERVICE'
                        CHECK (mode IN ('LIBRE_SERVICE','CLE_EN_MAIN')),
    quota_dossiers      integer     NOT NULL DEFAULT 50,
    quota_stockage_mo   integer     NOT NULL DEFAULT 5000,
    cle_api_llm         text,
    cree_le             timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE utilisateur (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email                text        NOT NULL UNIQUE,
    mot_de_passe_hash    text,
    nom_complet          text        NOT NULL,
    actif                boolean     NOT NULL DEFAULT true,
    totp_secret          text,
    totp_actif           boolean     NOT NULL DEFAULT false,
    echecs_connexion     integer     NOT NULL DEFAULT 0,
    verrouille_jusqu_a   timestamptz,
    super_admin          boolean     NOT NULL DEFAULT false,
    jeton_invitation     text UNIQUE,
    invitation_expire_le timestamptz,
    cree_le              timestamptz NOT NULL DEFAULT now()
);

-- Un utilisateur n'agit que dans les organisations dont il est membre.
CREATE TABLE membre (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    utilisateur_id      uuid NOT NULL REFERENCES utilisateur(id) ON DELETE CASCADE,
    role                text NOT NULL
                        CHECK (role IN ('ADMIN_ENTREPRISE','REDACTEUR','VALIDATEUR','LECTEUR','OPERATEUR_MANDATE')),
    cree_le             timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organisation_id, utilisateur_id, role)
);

-- Mandat du service cle en main : l'equipe n'agit que la ou il a ete accepte,
-- et l'entreprise peut le retirer a tout moment.
CREATE TABLE mandat (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    accorde_le          timestamptz NOT NULL DEFAULT now(),
    accorde_par         uuid REFERENCES utilisateur(id),
    retire_le           timestamptz,
    retire_par          uuid REFERENCES utilisateur(id)
);

-- ---------------------------------------------------------------- facturation

CREATE TABLE demande_inscription (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    raison_sociale      text NOT NULL,
    rccm                text NOT NULL,
    contact_nom         text NOT NULL,
    contact_email       text NOT NULL,
    contact_telephone   text,
    mode                text NOT NULL CHECK (mode IN ('LIBRE_SERVICE','CLE_EN_MAIN')),
    formule             text NOT NULL,
    statut              text NOT NULL DEFAULT 'RECUE'
                        CHECK (statut IN ('RECUE','PAYEE','RCCM_A_VERIFIER','ACTIVEE','REFUSEE')),
    organisation_id     uuid REFERENCES organisation(id),
    motif_refus         text,
    cree_le             timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE abonnement (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    mode                text NOT NULL CHECK (mode IN ('LIBRE_SERVICE','CLE_EN_MAIN')),
    formule             text NOT NULL,
    debut               date NOT NULL,
    fin                 date NOT NULL,
    statut              text NOT NULL DEFAULT 'ACTIF'
                        CHECK (statut IN ('ACTIF','EXPIRE','RESILIE')),
    rappel_j15_envoye   boolean NOT NULL DEFAULT false,
    rappel_j3_envoye    boolean NOT NULL DEFAULT false,
    CHECK (fin > debut)
);

CREATE TABLE paiement (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid REFERENCES organisation(id) ON DELETE SET NULL,
    demande_id          uuid REFERENCES demande_inscription(id) ON DELETE SET NULL,
    abonnement_id       uuid REFERENCES abonnement(id) ON DELETE SET NULL,
    montant             bigint NOT NULL CHECK (montant > 0),
    moyen               text   NOT NULL CHECK (moyen IN ('MOBILE_MONEY','CARTE','VIREMENT')),
    reference           text   NOT NULL UNIQUE,
    statut              text   NOT NULL DEFAULT 'EN_ATTENTE'
                        CHECK (statut IN ('EN_ATTENTE','CONFIRME','ECHOUE','REMBOURSE')),
    recu_chemin         text,
    confirme_le         timestamptz,
    cree_le             timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- fiche entreprise

CREATE TABLE entreprise (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL UNIQUE REFERENCES organisation(id) ON DELETE CASCADE,
    raison_sociale      text NOT NULL,
    sigle               text,
    rccm                text,
    compte_contribuable text,
    regime_fiscal       text,
    adresse             text,
    boite_postale       text,
    telephones          text,
    email               text,
    logo_chemin         text,
    entete_chemin       text,
    pied_de_page        text,
    maj_le              timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE representant (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    nom                 text NOT NULL,
    fonction            text NOT NULL,
    signature_chemin    text,
    cachet_chemin       text,
    signataire_defaut   boolean NOT NULL DEFAULT false
);

CREATE TABLE piece_admin (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    type                text NOT NULL
                        CHECK (type IN ('RCCM','DFE','ATTESTATION_PME','QUITUS_ARCOP','ATTESTATION_BANCAIRE',
                                        'ATTESTATION_FISCALE','CNPS','CAUTIONNEMENT','AUTRE')),
    libelle             text,
    numero              text,
    -- Montant porte par la piece quand elle en porte un : c'est le cas du
    -- cautionnement, que le DAO chiffre par lot. Nul si l'OCR n'a pas su lire.
    montant             bigint CHECK (montant IS NULL OR montant >= 0),
    fichier_chemin      text NOT NULL,
    date_emission       date,
    date_expiration     date,
    confirmee           boolean NOT NULL DEFAULT false,
    extraction_json     jsonb,
    cree_le             timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE personnel (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    nom                 text NOT NULL,
    poste               text NOT NULL,
    diplome             text,
    annees_experience   integer NOT NULL DEFAULT 0 CHECK (annees_experience >= 0),
    projets             text,
    cv_chemin           text,
    diplome_chemin      text
);

CREATE TABLE materiel (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    designation         text NOT NULL,
    marque              text,
    capacite            text,
    annee               integer,
    provenance          text NOT NULL DEFAULT 'POSSESSION'
                        CHECK (provenance IN ('POSSESSION','LOCATION')),
    justificatif_chemin text
);

CREATE TABLE reference_marche (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    maitre_ouvrage      text NOT NULL,
    objet               text NOT NULL,
    montant             bigint CHECK (montant IS NULL OR montant >= 0),
    annee               integer,
    justificatif_chemin text
);

-- ---------------------------------------------------------------- appel d'offres

CREATE TABLE appel_offres (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id       uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    numero_ao             text,
    intitule              text NOT NULL,
    autorite_contractante text,
    maitre_ouvrage        text,
    maitre_oeuvre         text,
    destinataire          text,
    financement           text,
    validite_offre_jours  integer CHECK (validite_offre_jours IS NULL OR validite_offre_jours > 0),
    regime_prix           text,
    tva_applicable        boolean,
    date_limite_depot     timestamptz,
    timbre_fiscal         bigint,
    dao_chemin            text,
    extraction_json       jsonb,
    statut                text NOT NULL DEFAULT 'EN_ANALYSE'
                          CHECK (statut IN ('EN_ANALYSE','A_VERIFIER','VERIFIE','ECHEC_ANALYSE')),
    erreur_analyse        text,
    cree_le               timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE lot (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    appel_offres_id     uuid NOT NULL REFERENCES appel_offres(id) ON DELETE CASCADE,
    numero              integer NOT NULL,
    intitule            text NOT NULL,
    localite            text,
    delai_mois          integer CHECK (delai_mois IS NULL OR delai_mois > 0),
    montant_garantie    bigint  CHECK (montant_garantie IS NULL OR montant_garantie >= 0),
    UNIQUE (appel_offres_id, numero)
);

CREATE TABLE exigence (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    appel_offres_id     uuid NOT NULL REFERENCES appel_offres(id) ON DELETE CASCADE,
    lot_id              uuid REFERENCES lot(id) ON DELETE CASCADE,
    type                text NOT NULL
                        CHECK (type IN ('PERSONNEL','MATERIEL','CHIFFRE_AFFAIRES','EXPERIENCE','PIECE','AUTRE')),
    libelle             text NOT NULL,
    valeur              text,
    quantite_min        integer,
    annees_min          integer,
    eliminatoire        boolean NOT NULL DEFAULT false,
    source_dao          text
);

-- ---------------------------------------------------------------- dossier

CREATE TABLE dossier (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id         uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    appel_offres_id         uuid NOT NULL REFERENCES appel_offres(id) ON DELETE CASCADE,
    lot_id                  uuid REFERENCES lot(id) ON DELETE CASCADE,
    statut                  text NOT NULL DEFAULT 'BROUILLON'
                            CHECK (statut IN ('BROUILLON','EN_VALIDATION','VALIDE','DEPOSE')),
    version                 integer NOT NULL DEFAULT 1 CHECK (version >= 1),
    representant_id         uuid REFERENCES representant(id),
    delai_execution_mois    integer,
    validite_offre_jours    integer,
    validite_garantie_jours integer,
    -- Le montant que portera la lettre de soumission, fige par le service de
    -- chiffrage a chaque changement de prix. Il doit toujours valoir la somme
    -- des lignes ; un ecart signale un PDF ou une lettre perimes.
    montant_soumission      bigint CHECK (montant_soumission IS NULL OR montant_soumission >= 0),
    auteur_id               uuid REFERENCES utilisateur(id),
    validateur_id           uuid REFERENCES utilisateur(id),
    modifie_par             uuid REFERENCES utilisateur(id),
    commentaire_validation  text,
    cree_le                 timestamptz NOT NULL DEFAULT now(),
    maj_le                  timestamptz NOT NULL DEFAULT now(),
    valide_le               timestamptz,
    depose_le               timestamptz,
    UNIQUE (appel_offres_id, lot_id, version)
);

-- Le montant n'est jamais saisi : la base le calcule, donc une erreur
-- arithmetique dans un DQE est impossible par construction.
CREATE TABLE ligne_dqe (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    dossier_id          uuid NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    section             text    NOT NULL,
    ordre_section       integer NOT NULL DEFAULT 0,
    numero_prix         text    NOT NULL,
    ordre               integer NOT NULL DEFAULT 0,
    designation         text,
    unite               text,
    quantite            numeric(14,3) NOT NULL DEFAULT 0 CHECK (quantite >= 0),
    prix_unitaire       bigint CHECK (prix_unitaire IS NULL OR prix_unitaire >= 0),
    montant             bigint GENERATED ALWAYS AS
                        (CASE WHEN prix_unitaire IS NULL THEN NULL
                              ELSE round(quantite * prix_unitaire)::bigint END) STORED,
    issue_du_dao        boolean NOT NULL DEFAULT true
);

-- Qui et quoi le dossier engage face a chaque exigence du DAO. Le
-- rapprochement les propose, l'utilisateur les confirme ou les change ; c'est
-- ce qui remplit le formulaire PER-1 et le formulaire materiel, laisses vides
-- dans les offres deposees jusqu'ici.
CREATE TABLE affectation_personnel (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    dossier_id          uuid NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    personnel_id        uuid NOT NULL REFERENCES personnel(id) ON DELETE CASCADE,
    exigence_id         uuid REFERENCES exigence(id) ON DELETE SET NULL,
    poste_exige         text NOT NULL,
    propose_automatiquement boolean NOT NULL DEFAULT true,
    UNIQUE (dossier_id, personnel_id, poste_exige)
);

CREATE TABLE affectation_materiel (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    dossier_id          uuid NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    materiel_id         uuid NOT NULL REFERENCES materiel(id) ON DELETE CASCADE,
    exigence_id         uuid REFERENCES exigence(id) ON DELETE SET NULL,
    designation_exigee  text NOT NULL,
    propose_automatiquement boolean NOT NULL DEFAULT true,
    UNIQUE (dossier_id, materiel_id, designation_exigee)
);

CREATE TABLE document_genere (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    dossier_id          uuid NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    type                text NOT NULL CHECK (type IN ('TECHNIQUE','FINANCIERE','RAPPORT_CONFORMITE','RECU')),
    format              text NOT NULL CHECK (format IN ('PDF','DOCX')),
    version             integer NOT NULL,
    brouillon           boolean NOT NULL DEFAULT true,
    empreinte_sha256    text NOT NULL,
    chemin              text NOT NULL,
    taille_octets       bigint,
    genere_le           timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE controle_resultat (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    dossier_id          uuid NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    code                text NOT NULL,
    libelle             text NOT NULL,
    niveau              text NOT NULL CHECK (niveau IN ('BLOQUANT','AVERTISSEMENT')),
    reussi              boolean NOT NULL,
    message             text,
    lien_correction     text,
    execute_le          timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- plateforme

CREATE TABLE modele (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                text NOT NULL,
    libelle             text NOT NULL,
    offre               text NOT NULL CHECK (offre IN ('TECHNIQUE','FINANCIERE','COMMUN')),
    fichier_jrxml       text NOT NULL,
    version             integer NOT NULL DEFAULT 1,
    ordre_sommaire      integer NOT NULL DEFAULT 0,
    actif               boolean NOT NULL DEFAULT true,
    UNIQUE (code, version)
);

CREATE TABLE parametre (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    cle                 text NOT NULL,
    valeur              text,
    UNIQUE (organisation_id, cle)
);

-- Journal non modifiable : aucune mise a jour, aucune suppression (voir V2).
CREATE TABLE journal (
    id                  bigserial PRIMARY KEY,
    organisation_id     uuid REFERENCES organisation(id) ON DELETE SET NULL,
    utilisateur_id      uuid REFERENCES utilisateur(id) ON DELETE SET NULL,
    action              text NOT NULL,
    objet_type          text,
    objet_id            text,
    detail              text,
    adresse_ip          text,
    survenu_le          timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- index

CREATE INDEX idx_membre_utilisateur   ON membre(utilisateur_id);
CREATE INDEX idx_membre_organisation  ON membre(organisation_id);
CREATE INDEX idx_piece_expiration     ON piece_admin(organisation_id, date_expiration);
CREATE INDEX idx_ao_organisation      ON appel_offres(organisation_id, cree_le DESC);
CREATE INDEX idx_lot_ao               ON lot(appel_offres_id);
CREATE INDEX idx_exigence_ao          ON exigence(appel_offres_id);
CREATE INDEX idx_dossier_organisation ON dossier(organisation_id, statut);
CREATE INDEX idx_dossier_ao           ON dossier(appel_offres_id);
CREATE INDEX idx_ligne_dqe_dossier    ON ligne_dqe(dossier_id, ordre_section, ordre);
CREATE INDEX idx_affect_pers_dossier  ON affectation_personnel(dossier_id);
CREATE INDEX idx_affect_mat_dossier   ON affectation_materiel(dossier_id);
CREATE INDEX idx_document_dossier     ON document_genere(dossier_id, version DESC);
CREATE INDEX idx_controle_dossier     ON controle_resultat(dossier_id);
CREATE INDEX idx_journal_organisation ON journal(organisation_id, survenu_le DESC);
CREATE INDEX idx_abonnement_fin       ON abonnement(fin) WHERE statut = 'ACTIF';
