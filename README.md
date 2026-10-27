# automatecdc

Plateforme de préparation des dossiers de réponse aux appels d'offres de
travaux publics.

À partir d'un DAO, elle produit l'offre technique et l'offre financière : les
données de l'entreprise sont saisies une fois et reprises dans toutes les
pièces, les montants sont calculés par la base, et des contrôles de conformité
interdisent la validation d'un dossier qui serait rejeté.

> **État : MVP en cours.** Le socle est validé contre un vrai PostgreSQL et les
> écrans principaux existent. Ce qui n'est pas terminé est listé à la fin, sans
> enjoliver.

## Les trois idées qui tiennent le reste

**La base porte l'isolation, pas le code.** Chaque table métier a une colonne
`organisation_id` et une politique PostgreSQL. Même une requête applicative
sans filtre ne renvoie rien d'une autre entreprise. Un test le vérifie en SQL
brut, sans passer par l'application.

**Les montants ne sont jamais saisis.** Le montant d'une ligne de bordereau est
une colonne générée : PostgreSQL refuse qu'on l'écrive. L'erreur arithmétique
n'est pas rattrapée, elle est impossible — ce qui compte, puisque la commission
corrige ces erreurs elle-même et que la correction change le classement.

**L'IA lit, elle ne rédige pas.** L'extraction du DAO passe en « à vérifier »,
un humain la relit, et seules des données relues alimentent les documents. Si
l'extraction échoue, le DAO reste déposé et les champs se saisissent à la main.

## Démarrer

### Ce qu'il faut

- Docker et Docker Compose
- JDK 21 et Maven 3.9 pour le développement

### En production

```bash
cp .env.example .env     # puis remplir chaque ligne
docker compose up -d --build
```

Les secrets se génèrent avec `openssl rand -base64 36`.

> `CLE_CHIFFREMENT_FICHIERS` est **irremplaçable**. Les fichiers sont chiffrés
> au repos avec elle : perdue, aucun DAO ni aucun PDF archivé ne peut plus être
> relu. Sauvegardez-la ailleurs que sur le VPS, et ne la régénérez jamais.

Cinq conteneurs démarrent ; seul Caddy publie des ports.

| Conteneur | Rôle |
|---|---|
| `caddy` | HTTPS, unique point d'entrée |
| `app` | Interface, droits, tâches de fond, rendu Jasper et Word |
| `postgres` | Données, isolation par Row-Level Security |
| `ocr` | OCRmyPDF en français, pour les documents scannés |
| `gotenberg` | LibreOffice, pour convertir les `.doc` |

### En développement

```bash
./infra/test/base-de-test.sh demarrer     # PostgreSQL 16 sur le port 55432

export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/automatecdc
export SPRING_DATASOURCE_USERNAME=app
export SPRING_DATASOURCE_PASSWORD=app-de-test
export APP_FLYWAY_UTILISATEUR=migration
export APP_FLYWAY_MOT_DE_PASSE=migration-de-test
export APP_CLE_CHIFFREMENT_FICHIERS=cle-locale-non-sensible
export APP_CLE_LIENS_SIGNES=cle-locale-non-sensible
export APP_IA_ACTIVE=false

mvn spring-boot:run
```

L'application écoute sur <http://localhost:8080>.

Le premier compte est le super-administrateur créé par la migration
(`admin@automatecdc.local`), **sans mot de passe** : il le choisit lui-même par
son lien d'invitation. Personne, pas même l'exploitant, ne connaît un mot de
passe qu'il n'a pas choisi.

## Les tests

```bash
mvn test                                   # tests unitaires

./infra/test/base-de-test.sh demarrer      # puis, avec une vraie base :
mvn test -Dtest.postgres.url=jdbc:postgresql://localhost:55432/automatecdc \
         -Dtest.postgres.mdp.app=app-de-test \
         -Dtest.postgres.mdp.migration=migration-de-test \
         -Dtest.postgres.mdp.lecture=lecture-de-test
```

Sans base fournie, les tests d'intégration **s'ignorent** au lieu d'échouer.
Une base absente apparaît alors comme un test ignoré, jamais comme un succès.

Ce que les tests d'intégration vérifient, et que rien d'autre ne peut prouver :
migration d'une base vierge, validation des entités contre le schéma, lecture
croisée impossible entre entreprises, écriture au nom d'une autre refusée,
absence de contexte qui ne donne rien plutôt que tout, montant recalculé et non
écrit, journal en ajout seul, compte applicatif sans droit sur la structure.

`ParcoursCompletTest` enchaîne le parcours entier dans l'ordre où l'interface
appelle les services : appel d'offres saisi, coffre rempli, dossier créé,
rapprochement, prix, contrôles, génération, archive, validation, version n+1.
L'extraction par le modèle y est **coupée** : le parcours doit fonctionner sans
elle, car une plateforme qui ne sait plus produire un dossier parce qu'un
service distant est indisponible ne vaut rien un jour d'échéance.

## Les documents

Trois provenances coexistent.

**Les rapports Jasper** sont livrés avec l'application et communs à toutes les
entreprises : ce sont les formulaires imposés par les bailleurs. Personne ne
peut y glisser une clause.

**Les modèles Word** sont déposés par l'entreprise, dans *Modèles de documents*.
On les remplit en conservant leur mise en page plutôt que de les reconstruire :
un dossier reconstruit ne ressemble plus à celui que la commission a l'habitude
de lire. Les champs se notent `{{NOM_DU_CHAMP}}` ; une ligne de tableau marquée
`{{#LISTE:personnel}}` se répète une fois par élément. L'application lit les
champs au dépôt — rien à déclarer ailleurs.

**Les pièces du coffre** (RCCM, quitus, attestations) partent telles quelles.

Un champ sans valeur n'est pas laissé en blanc : il s'imprime
`[à compléter : ...]` et remonte dans la liste jointe à l'archive. Un blanc
silencieux irait jusqu'au dépôt.

## Ce qui reste à faire

- **WF2, WF3, WF4** : la constitution, la génération déclenchée et la veille
  quotidienne (alertes J-30, rappels J-3 et J-1, sauvegarde) ne sont pas encore
  branchées sur l'ordonnanceur.
- **Modèles Jasper manquants** : sommaire, BPU, renseignements, antécédents,
  planning, reçu. Ils ne sont volontairement pas déclarés au catalogue : un
  `.jrxml` déclaré mais absent ferait échouer le rendu au moment du clic.
- **Inscription et paiement** : le formulaire public, l'activation par le
  super-administrateur, l'invitation par courriel et l'agrégateur Mobile Money
  restent à écrire. Aucune transaction n'est affichée comme réussie sans
  confirmation vérifiable du fournisseur.
- **Sauvegarde externe et restauration testée**, liens de téléchargement signés,
  export et suppression des données d'une organisation.
- **Enrôlement 2FA** : le mécanisme TOTP est implémenté et vérifié à la
  connexion, mais l'écran qui affiche le QR code reste à faire.

## Décisions qui ne dépendent pas du code

Elles bloquent le pilote quoi qu'il arrive : l'agrégateur Mobile Money et ses
accès, les tarifs, l'hébergeur du VPS et le lieu de la sauvegarde externe.

## Licence et données

Aucune identité d'entreprise n'est codée dans l'application. Raison sociale,
sigle, RCCM, signataires et coordonnées vivent en base, par organisation, et se
modifient sans toucher au code ni aux modèles.

L'application ne fabrique jamais une référence, un chiffre d'affaires, une
attestation, un CV ni une signature. Chaque donnée vient d'une source
identifiable : le formulaire, la fiche de l'entreprise, une pièce justificative
ou le DAO. Les documents produits doivent être relus et signés par un humain
avant dépôt.
