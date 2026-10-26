-- Les modeles Jasper sont communs a toutes les organisations : une piece = un
-- rapport, assembles dans l'ordre du sommaire.
--
-- Six pieces partagent lettre_type.jrxml et deux partagent tableau_moyens.jrxml.
-- Ce n'est pas une economie de fichiers : ce qui change d'une lettre type a
-- l'autre, c'est le titre et le corps, et les avoir a un seul endroit evite que
-- l'en-tete derive d'une piece a l'autre - exactement ce qui est arrive dans
-- les offres redigees a la main.
--
-- Ne figurent ici que les modeles qui existent. Le sommaire, le BPU, la fiche
-- de renseignements, les antecedents, le planning et le recu de paiement
-- restent a ecrire ; les declarer avant de les avoir ferait echouer le rendu
-- au moment ou quelqu'un clique.

INSERT INTO modele (code, libelle, offre, fichier_jrxml, ordre_sommaire) VALUES
    ('PAGE_GARDE',          'Page de garde',                             'COMMUN',     'rapports/page_garde.jrxml',         1),
    ('DELAI_EXECUTION',     'Engagement sur le delai d''execution',      'TECHNIQUE',  'rapports/lettre_type.jrxml',       20),
    ('VALIDITE_OFFRE',      'Engagement sur la validite de l''offre',    'TECHNIQUE',  'rapports/lettre_type.jrxml',       21),
    ('VALIDITE_GARANTIE',   'Engagement sur la validite de la garantie', 'TECHNIQUE',  'rapports/lettre_type.jrxml',       22),
    ('POUVOIRS',            'Pouvoirs du signataire',                    'TECHNIQUE',  'rapports/lettre_type.jrxml',       23),
    ('CONFLIT_INTERETS',    'Declaration de conflit d''interets',        'TECHNIQUE',  'rapports/lettre_type.jrxml',       24),
    ('DECLARATION_HONNEUR', 'Declaration sur l''honneur',                'TECHNIQUE',  'rapports/lettre_type.jrxml',       25),
    ('PERSONNEL_CLE',       'Personnel cle (PER-1)',                     'TECHNIQUE',  'rapports/tableau_moyens.jrxml',    28),
    ('MATERIEL',            'Materiel (MAT)',                            'TECHNIQUE',  'rapports/tableau_moyens.jrxml',    29),
    ('LETTRE_SOUMISSION',   'Lettre de soumission de l''offre',          'FINANCIERE', 'rapports/lettre_soumission.jrxml', 10),
    ('DQE',                 'Detail quantitatif et estimatif',           'FINANCIERE', 'rapports/dqe.jrxml',               11),
    ('RAPPORT_CONFORMITE',  'Rapport de conformite',                     'COMMUN',     'rapports/rapport_conformite.jrxml',90);

-- Le super-administrateur est cree sans mot de passe : il le choisit lui-meme
-- via le lien d'invitation imprime au premier demarrage. Personne, pas meme
-- l'exploitant, ne connait un mot de passe qu'il n'a pas choisi.
INSERT INTO utilisateur (email, nom_complet, super_admin, actif, mot_de_passe_hash)
VALUES ('admin@automatecdc.local', 'Super administrateur', true, true, NULL)
ON CONFLICT (email) DO NOTHING;
