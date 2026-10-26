-- Trois comptes PostgreSQL, trois pouvoirs. Execute une seule fois, a la
-- creation du cluster, par le superutilisateur de l'image officielle.
--
--   migration : proprietaire du schema, seul a pouvoir le modifier. Utilise
--               par Flyway au demarrage, puis plus jamais.
--   app       : lit et ecrit les donnees, aucun droit sur la structure.
--               C'est le compte de l'application.
--   lecture   : lecture seule, pour la supervision et les exports.
--
-- Aucun des trois n'est superutilisateur, donc aucun ne contourne la
-- Row-Level Security.

\set mdp_app        `echo "$MDP_APP"`
\set mdp_lecture    `echo "$MDP_LECTURE"`
\set mdp_migration  `echo "$MDP_MIGRATION"`

CREATE ROLE migration LOGIN PASSWORD :'mdp_migration';
CREATE ROLE app       LOGIN PASSWORD :'mdp_app';
CREATE ROLE lecture   LOGIN PASSWORD :'mdp_lecture';

-- Le schema appartient a migration : app ne peut pas creer ni supprimer de table.
ALTER SCHEMA public OWNER TO migration;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO app, lecture;

-- JobRunr cree ses propres tables au demarrage de l'application ; elles ne
-- contiennent pas de donnees metier et vivent dans leur propre schema, dont
-- app est proprietaire.
CREATE SCHEMA IF NOT EXISTS taches AUTHORIZATION app;
