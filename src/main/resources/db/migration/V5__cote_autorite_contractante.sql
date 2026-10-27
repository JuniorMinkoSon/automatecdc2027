-- La plateforme sert desormais les deux cotes du marche public.
--
-- Jusqu'ici un appel d'offres etait toujours celui d'un autre : on le
-- televersait pour y repondre. Une collectivite qui publie ses propres marches
-- a le probleme symetrique, et c'est le meme : des dizaines de pages dont une
-- vingtaine de champs seulement changent, recopies a la main d'une section a
-- l'autre.
--
-- Plutot que de creer une seconde table, le meme appel_offres porte les deux
-- roles. L'intitule, le numero, les lots, les delais et les garanties sont les
-- memes objets des deux cotes ; seul change qui les ecrit. Les dupliquer
-- aurait garanti qu'ils divergent.

ALTER TABLE appel_offres
    ADD COLUMN role text NOT NULL DEFAULT 'REPONSE'
        CHECK (role IN ('REPONSE', 'PUBLICATION'));

COMMENT ON COLUMN appel_offres.role IS
    'REPONSE : marche publie par un tiers, auquel on repond. '
    'PUBLICATION : marche que l''organisation publie elle-meme.';

-- Champs propres a la publication d'un dossier d'appel d'offres. Tous nuls
-- pour un appel d'offres auquel on se contente de repondre.
ALTER TABLE appel_offres
    ADD COLUMN ligne_budgetaire       text,
    ADD COLUMN mode_passation         text,
    ADD COLUMN prix_dossier           bigint CHECK (prix_dossier IS NULL OR prix_dossier >= 0),
    ADD COLUMN moyen_paiement_dossier text,
    ADD COLUMN adresse_retrait        text,
    ADD COLUMN plateforme_depot       text,
    ADD COLUMN url_depot              text,
    ADD COLUMN ouverture_plis         timestamptz,
    ADD COLUMN lieu_ouverture         text,
    ADD COLUMN contacts               text,
    ADD COLUMN qualifications_exigees text,
    -- L'avis annonce une duree de validite des offres. Quand elle differe de
    -- celle du reglement, l'offre que le candidat redige est non conforme sans
    -- qu'il ait commis d'erreur : c'est le dossier qui se contredit.
    ADD COLUMN validite_annoncee_jours integer
        CHECK (validite_annoncee_jours IS NULL OR validite_annoncee_jours > 0),
    ADD COLUMN redevance_arcop        numeric(5,3),
    ADD COLUMN reference_code_marches text;

CREATE INDEX idx_ao_role ON appel_offres (organisation_id, role, cree_le DESC);

-- Les sections d'un dossier d'appel d'offres.
--
-- Le code des marches distingue ce qui ne se modifie pas - Instructions aux
-- candidats, Cahier des clauses administratives generales - de ce qui est
-- propre a chaque marche. Les premieres sont livrees avec la plateforme et
-- verrouillees : une collectivite qui y glisserait une clause produirait un
-- dossier attaquable. Les secondes se redigent, et c'est la que le temps se
-- perd aujourd'hui.
CREATE TABLE section_dao (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    appel_offres_id     uuid NOT NULL REFERENCES appel_offres(id) ON DELETE CASCADE,
    code                text NOT NULL,
    intitule            text NOT NULL,
    ordre               integer NOT NULL,
    modifiable          boolean NOT NULL DEFAULT true,
    contenu             text,
    -- Un modele televerse peut fournir la section, pour les collectivites qui
    -- ont deja leurs propres cahiers des charges techniques.
    modele_id           uuid REFERENCES modele(id) ON DELETE SET NULL,
    maj_le              timestamptz NOT NULL DEFAULT now(),
    UNIQUE (appel_offres_id, code)
);

CREATE INDEX idx_section_dao_ao ON section_dao (appel_offres_id, ordre);

ALTER TABLE section_dao ENABLE ROW LEVEL SECURITY;
ALTER TABLE section_dao FORCE ROW LEVEL SECURITY;
CREATE POLICY isolation_organisation ON section_dao
    USING (organisation_id = app_organisation())
    WITH CHECK (organisation_id = app_organisation());

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app') THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON section_dao TO app';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lecture') THEN
        EXECUTE 'GRANT SELECT ON section_dao TO lecture';
    END IF;
END $$;
