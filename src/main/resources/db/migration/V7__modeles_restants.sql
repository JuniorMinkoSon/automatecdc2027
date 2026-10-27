-- Les quatre modeles qui manquaient a l'offre technique et a la facturation.
--
-- Comme les precedents, ils ne sont declares qu'une fois ecrits : un .jrxml
-- declare mais absent fait echouer le rendu au moment du clic, c'est-a-dire au
-- pire moment.

INSERT INTO modele (code, libelle, offre, format, categorie, fichier_jrxml, ordre_sommaire) VALUES
    ('RENSEIGNEMENTS', 'Renseignements sur le soumissionnaire', 'TECHNIQUE', 'JASPER',
     'RENSEIGNEMENT', 'rapports/renseignements.jrxml', 26),
    ('ANTECEDENTS',    'Antécédents de marchés non exécutés',   'TECHNIQUE', 'JASPER',
     'RENSEIGNEMENT', 'rapports/antecedents.jrxml',    27),
    ('PLANNING',       'Planning d''exécution des travaux',     'TECHNIQUE', 'JASPER',
     'METHODOLOGIE',  'rapports/planning.jrxml',       30),
    ('RECU_PAIEMENT',  'Reçu de paiement',                      'COMMUN',    'JASPER',
     'AUTRE',         'rapports/recu_paiement.jrxml',  95);
