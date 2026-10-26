-- Les modeles Jasper sont communs a toutes les organisations : une piece = un
-- rapport, assembles par le rapport maitre dans l'ordre du sommaire.

INSERT INTO modele (code, libelle, offre, fichier_jrxml, ordre_sommaire) VALUES
    ('PAGE_GARDE',        'Page de garde',                            'COMMUN',     'rapports/page_garde.jrxml',        1),
    ('SOMMAIRE',          'Sommaire',                                 'COMMUN',     'rapports/sommaire.jrxml',          2),
    ('LETTRE_SOUMISSION', 'Lettre de soumission de l''offre',         'FINANCIERE', 'rapports/lettre_soumission.jrxml', 10),
    ('DQE',               'Detail quantitatif et estimatif',          'FINANCIERE', 'rapports/dqe.jrxml',               11),
    ('BPU',               'Bordereau des prix unitaires',             'FINANCIERE', 'rapports/bpu.jrxml',               12),
    ('DELAI_EXECUTION',   'Engagement sur le delai d''execution',     'TECHNIQUE',  'rapports/delai_execution.jrxml',   20),
    ('VALIDITE_OFFRE',    'Engagement sur la validite de l''offre',   'TECHNIQUE',  'rapports/validite_offre.jrxml',    21),
    ('VALIDITE_GARANTIE', 'Engagement sur la validite de la garantie','TECHNIQUE',  'rapports/validite_garantie.jrxml', 22),
    ('POUVOIRS',          'Pouvoirs du signataire',                   'TECHNIQUE',  'rapports/pouvoirs.jrxml',          23),
    ('CONFLIT_INTERETS',  'Declaration de conflit d''interets',       'TECHNIQUE',  'rapports/conflit_interets.jrxml',  24),
    ('DECLARATION_HONNEUR','Declaration sur l''honneur',              'TECHNIQUE',  'rapports/declaration_honneur.jrxml',25),
    ('RENSEIGNEMENTS',    'Renseignements sur le soumissionnaire',    'TECHNIQUE',  'rapports/renseignements.jrxml',    26),
    ('ANTECEDENTS',       'Antecedents de marches non executes',      'TECHNIQUE',  'rapports/antecedents.jrxml',       27),
    ('PERSONNEL_CLE',     'Personnel cle (PER-1)',                    'TECHNIQUE',  'rapports/personnel_cle.jrxml',     28),
    ('MATERIEL',          'Materiel (MAT)',                           'TECHNIQUE',  'rapports/materiel.jrxml',          29),
    ('PLANNING',          'Planning d''execution',                    'TECHNIQUE',  'rapports/planning.jrxml',          30),
    ('RAPPORT_CONFORMITE','Rapport de conformite',                    'COMMUN',     'rapports/rapport_conformite.jrxml',90),
    ('RECU_PAIEMENT',     'Recu de paiement',                         'COMMUN',     'rapports/recu_paiement.jrxml',     91);

-- Le super-administrateur est cree sans mot de passe : il le choisit lui-meme
-- via le lien d'invitation imprime au premier demarrage. Personne, pas meme
-- l'exploitant, ne connait un mot de passe qu'il n'a pas choisi.
INSERT INTO utilisateur (email, nom_complet, super_admin, actif, mot_de_passe_hash)
VALUES ('admin@automatecdc.local', 'Super administrateur', true, true, NULL)
ON CONFLICT (email) DO NOTHING;
