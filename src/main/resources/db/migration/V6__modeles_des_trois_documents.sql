-- Les modeles qui manquaient aux trois documents.
--
-- Le sommaire sert aux trois : dossier d'appel d'offres, offre technique,
-- offre financiere. Le separateur de lot aussi, des qu'un marche est alloti.
-- L'avis n'existe que du cote de l'autorite contractante, le bordereau des
-- prix unitaires que du cote du soumissionnaire.
--
-- Ils ne sont declares que maintenant parce qu'ils existent maintenant :
-- declarer un .jrxml absent ferait echouer le rendu au moment du clic, c'est-
-- a-dire au pire moment.

-- ---------------------------------------------------------------- correction V4
--
-- V4 avait pose FORCE ROW LEVEL SECURITY sur modele. C'etait de trop : FORCE
-- soumet aussi le proprietaire du schema a la politique, et la politique
-- d'insertion exige organisation_id = app_organisation(). Un modele commun
-- porte organisation_id nul, donc la comparaison rend NULL, donc l'insertion
-- est refusee - y compris a migration, qui est precisement le compte charge de
-- livrer les modeles communs.
--
-- Concretement, aucune migration ne pouvait plus ajouter un modele partage.
-- ENABLE suffit : app n'est pas proprietaire du schema, il reste donc
-- integralement soumis aux politiques et ne peut toujours pas ecrire un modele
-- commun. Seul migration, qui ne sert qu'au deploiement, passe outre.
ALTER TABLE modele NO FORCE ROW LEVEL SECURITY;

INSERT INTO modele (code, libelle, offre, format, categorie, fichier_jrxml, ordre_sommaire) VALUES
    ('SOMMAIRE',       'Sommaire',                     'COMMUN',     'JASPER', 'AUTRE',
     'rapports/sommaire.jrxml',          2),
    ('SEPARATEUR_LOT', 'Page de séparation de lot',    'COMMUN',     'JASPER', 'AUTRE',
     'rapports/separateur_lot.jrxml',    3),
    ('AVIS_AAO',       'Avis d''appel d''offres',      'COMMUN',     'JASPER', 'ADMINISTRATIF',
     'rapports/avis_appel_offres.jrxml', 1),
    ('BPU',            'Bordereau des prix unitaires', 'FINANCIERE', 'JASPER', 'FINANCIER',
     'rapports/bpu.jrxml',              12);
