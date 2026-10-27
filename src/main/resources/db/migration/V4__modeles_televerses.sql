-- Les modeles de reference televerses par une entreprise.
--
-- Jusqu'ici, modele ne decrivait que les rapports Jasper livres avec
-- l'application. Mais l'essentiel d'un dossier de reponse existe deja chez le
-- soumissionnaire, sous forme de documents Word mis au point au fil des
-- marches : formulaires du bailleur, lettres types, tableaux de moyens. Les
-- reconstruire en Jasper les priverait de leur mise en page, et un dossier qui
-- ne ressemble plus a celui que la commission a l'habitude de lire se remarque.
--
-- On les range donc dans la meme table : un modele est soit un rapport Jasper
-- commun a tous, soit un document Word propre a une organisation, soit une
-- piece justificative simplement jointe. L'ordre du sommaire les gouverne tous.

ALTER TABLE modele
    ADD COLUMN organisation_id  uuid REFERENCES organisation(id) ON DELETE CASCADE,
    ADD COLUMN categorie        text NOT NULL DEFAULT 'AUTRE'
                                CHECK (categorie IN ('ADMINISTRATIF','ENGAGEMENT','RENSEIGNEMENT',
                                                     'MOYENS','FINANCIER','METHODOLOGIE','AUTRE')),
    ADD COLUMN format           text NOT NULL DEFAULT 'JASPER'
                                CHECK (format IN ('JASPER','DOCX','PIECE_JOINTE')),
    ADD COLUMN fichier_chemin   text,
    -- Les champs {{...}} trouves dans le document, qui disent au formulaire
    -- quoi demander. Lus du modele, jamais codes en dur.
    ADD COLUMN champs_detectes  jsonb,
    ADD COLUMN cree_le          timestamptz NOT NULL DEFAULT now();

-- Un modele Jasper porte un .jrxml, un modele Word porte un fichier : ni l'un
-- ni l'autre n'est obligatoire dans l'absolu, mais l'un des deux doit etre la.
ALTER TABLE modele ALTER COLUMN fichier_jrxml DROP NOT NULL;

ALTER TABLE modele ADD CONSTRAINT modele_a_une_source CHECK (
    (format = 'JASPER'        AND fichier_jrxml IS NOT NULL)
 OR (format = 'DOCX'          AND fichier_chemin IS NOT NULL)
 OR (format = 'PIECE_JOINTE')
);

-- Un modele commun et un modele d'entreprise peuvent porter le meme code :
-- c'est ainsi qu'une entreprise remplace une lettre type livree par la sienne.
ALTER TABLE modele DROP CONSTRAINT modele_code_version_key;
CREATE UNIQUE INDEX idx_modele_unicite
    ON modele (coalesce(organisation_id, '00000000-0000-0000-0000-000000000000'::uuid),
               code, version);

CREATE INDEX idx_modele_organisation ON modele (organisation_id, ordre_sommaire);

-- Les modeles communs se lisent de partout ; ceux d'une organisation, d'elle
-- seule. L'ecriture reste cantonnee a l'organisation : personne ne modifie un
-- modele commun depuis l'application.
ALTER TABLE modele ENABLE ROW LEVEL SECURITY;
ALTER TABLE modele FORCE ROW LEVEL SECURITY;

CREATE POLICY lecture_modeles ON modele FOR SELECT
    USING (organisation_id IS NULL OR organisation_id = app_organisation());

CREATE POLICY ecriture_modeles ON modele FOR INSERT
    WITH CHECK (organisation_id = app_organisation());

CREATE POLICY maj_modeles ON modele FOR UPDATE
    USING (organisation_id = app_organisation())
    WITH CHECK (organisation_id = app_organisation());

CREATE POLICY suppression_modeles ON modele FOR DELETE
    USING (organisation_id = app_organisation());
