-- De quoi alimenter les trois pieces qui restaient sans donnees, et donner au
-- DPAO la forme qu'il a reellement.

-- ---------------------------------------------------------------- antecedents
--
-- Le formulaire propose deux declarations exclusives : aucun marche inexecute,
-- ou la liste. Les deux ne peuvent pas coexister, et c'est le soumissionnaire
-- qui declare - la plateforme ne peut pas le deduire, puisqu'un marche
-- inexecute ne laisse pas de trace chez celui qui ne l'a pas execute.
ALTER TABLE dossier
    ADD COLUMN sans_antecedent    boolean NOT NULL DEFAULT true,
    ADD COLUMN antecedents_detail text;

COMMENT ON COLUMN dossier.sans_antecedent IS
    'Declaration du soumissionnaire. Vrai : aucun marche non execute depuis la '
    'date de reference. Faux : la liste doit figurer dans antecedents_detail.';

-- ---------------------------------------------------------------- fiche entreprise
--
-- Deux champs que le formulaire de renseignements exige et que la fiche ne
-- portait pas. Les laisser vides imprimait « a completer » a chaque
-- generation.
ALTER TABLE entreprise
    ADD COLUMN annee_enregistrement integer
        CHECK (annee_enregistrement IS NULL OR annee_enregistrement BETWEEN 1900 AND 2200),
    ADD COLUMN pays                 text;

-- ---------------------------------------------------------------- planning
--
-- Une tache, son mois de debut, sa duree. La commission verifie une seule
-- chose : que la derniere tache s'acheve dans le delai engage. Cette forme la
-- lui donne sans qu'elle ait a compter des cases, et permet a la plateforme de
-- signaler un depassement avant le depot.
CREATE TABLE tache_planning (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    dossier_id      uuid NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    libelle         text    NOT NULL,
    debut_mois      integer NOT NULL DEFAULT 1 CHECK (debut_mois >= 1),
    duree_mois      integer NOT NULL DEFAULT 1 CHECK (duree_mois >= 1),
    ordre           integer NOT NULL DEFAULT 0
);

CREATE INDEX idx_tache_planning_dossier ON tache_planning (dossier_id, ordre);

ALTER TABLE tache_planning ENABLE ROW LEVEL SECURITY;
ALTER TABLE tache_planning FORCE ROW LEVEL SECURITY;
CREATE POLICY isolation_organisation ON tache_planning
    USING (organisation_id = app_organisation())
    WITH CHECK (organisation_id = app_organisation());

-- ---------------------------------------------------------------- DPAO
--
-- Les donnees particulieres de l'appel d'offres ne sont pas de la prose : ce
-- sont des reponses numerotees, chacune rattachee a un article des
-- instructions aux candidats. Les saisir dans une zone de texte libre revenait
-- a retaper un formulaire, c'est-a-dire a reproduire exactement ce que la
-- plateforme sert a supprimer.
--
-- La liste des articles est posee a la creation du dossier ; l'autorite
-- contractante ne remplit que les valeurs.
CREATE TABLE donnee_dpao (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id uuid NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    appel_offres_id uuid NOT NULL REFERENCES appel_offres(id) ON DELETE CASCADE,
    reference_ic    text    NOT NULL,
    libelle         text    NOT NULL,
    valeur          text,
    ordre           integer NOT NULL DEFAULT 0,
    -- Certaines valeurs sont deduites du marche lui-meme : les recopier ici
    -- les ferait diverger au premier changement.
    derivee         boolean NOT NULL DEFAULT false,
    UNIQUE (appel_offres_id, reference_ic)
);

CREATE INDEX idx_dpao_ao ON donnee_dpao (appel_offres_id, ordre);

ALTER TABLE donnee_dpao ENABLE ROW LEVEL SECURITY;
ALTER TABLE donnee_dpao FORCE ROW LEVEL SECURITY;
CREATE POLICY isolation_organisation ON donnee_dpao
    USING (organisation_id = app_organisation())
    WITH CHECK (organisation_id = app_organisation());

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app') THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON tache_planning, donnee_dpao TO app';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lecture') THEN
        EXECUTE 'GRANT SELECT ON tache_planning, donnee_dpao TO lecture';
    END IF;
END $$;
