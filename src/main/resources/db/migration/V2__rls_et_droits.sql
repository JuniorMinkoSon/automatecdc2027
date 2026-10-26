-- Isolation entre entreprises, portee par la base et non par l'application :
-- meme en cas de bug applicatif, PostgreSQL refuse de renvoyer les lignes
-- d'une autre organisation.
--
-- L'application ouvre chaque transaction par
--     SET LOCAL app.organisation_id = '<uuid>';
-- et, pour le super-administrateur de la plateforme,
--     SET LOCAL app.super_admin = 'on';
--
-- Le super-administrateur voit les comptes et les paiements, jamais les
-- dossiers : aucune politique de dossier ne le mentionne.

CREATE OR REPLACE FUNCTION app_organisation() RETURNS uuid
    LANGUAGE sql STABLE AS
$$ SELECT nullif(current_setting('app.organisation_id', true), '')::uuid $$;

CREATE OR REPLACE FUNCTION app_super_admin() RETURNS boolean
    LANGUAGE sql STABLE AS
$$ SELECT coalesce(nullif(current_setting('app.super_admin', true), '')::boolean, false) $$;

-- Tables strictement cloisonnees par organisation.
--
-- Deux tables manquent volontairement a cette liste : membre et mandat. Ce
-- sont l'annuaire des droits, et ils doivent etre lus AVANT qu'une
-- organisation soit connue - c'est leur lecture qui apprend a quelle
-- organisation l'utilisateur appartient. Les y soumettre rendrait la connexion
-- impossible : la base refuserait de dire a quelqu'un ou il a le droit
-- d'entrer. Elles ne portent aucune donnee metier, seulement des triplets
-- (utilisateur, organisation, role), et l'application les filtre par
-- l'utilisateur authentifie.
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'abonnement','entreprise','representant','piece_admin',
        'personnel','materiel','reference_marche','appel_offres','lot','exigence',
        'dossier','ligne_dqe','affectation_personnel','affectation_materiel',
        'document_genere','controle_resultat','parametre'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY isolation_organisation ON %I
                 USING (organisation_id = app_organisation())
                 WITH CHECK (organisation_id = app_organisation())', t);
    END LOOP;
END $$;

-- Paiements et journal : l'organisation les voit, le super-administrateur aussi,
-- parce que c'est lui qui encaisse et qui audite les connexions.
ALTER TABLE paiement ENABLE ROW LEVEL SECURITY;
ALTER TABLE paiement FORCE ROW LEVEL SECURITY;
CREATE POLICY isolation_organisation ON paiement
    USING (app_super_admin() OR organisation_id = app_organisation())
    WITH CHECK (app_super_admin() OR organisation_id = app_organisation());

ALTER TABLE journal ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal FORCE ROW LEVEL SECURITY;
CREATE POLICY isolation_organisation ON journal
    USING (app_super_admin() OR organisation_id = app_organisation())
    WITH CHECK (true);

-- Les demandes d'inscription arrivent avant toute organisation : seul le
-- super-administrateur les lit.
ALTER TABLE demande_inscription ENABLE ROW LEVEL SECURITY;
ALTER TABLE demande_inscription FORCE ROW LEVEL SECURITY;
CREATE POLICY super_admin_seul ON demande_inscription
    USING (app_super_admin() OR organisation_id = app_organisation())
    WITH CHECK (true);

-- Une organisation ne lit que sa propre fiche.
ALTER TABLE organisation ENABLE ROW LEVEL SECURITY;
ALTER TABLE organisation FORCE ROW LEVEL SECURITY;
CREATE POLICY isolation_organisation ON organisation
    USING (app_super_admin() OR id = app_organisation())
    WITH CHECK (app_super_admin() OR id = app_organisation());

-- ---------------------------------------------------------------- journal non modifiable

CREATE OR REPLACE FUNCTION journal_immuable() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'Le journal est en ajout seul : ni modification ni suppression.';
END $$;

CREATE TRIGGER journal_pas_de_modification
    BEFORE UPDATE OR DELETE ON journal
    FOR EACH ROW EXECUTE FUNCTION journal_immuable();

-- ---------------------------------------------------------------- droits

-- Trois comptes, trois pouvoirs : app ecrit les donnees mais ne touche pas a la
-- structure, lecture ne fait que lire, migration (proprietaire, utilise ici)
-- est le seul a modifier la structure et n'est pas utilise par l'application.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app') THEN
        EXECUTE 'GRANT USAGE ON SCHEMA public TO app';
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app';
        EXECUTE 'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app';
        EXECUTE 'GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO app';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public
                 GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public
                 GRANT USAGE, SELECT ON SEQUENCES TO app';
        -- Ajout seul sur le journal, y compris pour l'application.
        EXECUTE 'REVOKE UPDATE, DELETE ON journal FROM app';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lecture') THEN
        EXECUTE 'GRANT USAGE ON SCHEMA public TO lecture';
        EXECUTE 'GRANT SELECT ON ALL TABLES IN SCHEMA public TO lecture';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO lecture';
    END IF;
END $$;
