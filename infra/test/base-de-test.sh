#!/usr/bin/env bash
#
# Demarre une base PostgreSQL de test identique a celle de production, avec le
# vrai script d'amorcage des roles.
#
#   ./infra/test/base-de-test.sh demarrer   cree la base et affiche les options Maven
#   ./infra/test/base-de-test.sh arreter    supprime la base et ses donnees
#   ./infra/test/base-de-test.sh etat       dit si elle tourne
#
# Volontairement sans volume : chaque demarrage repart d'une base vierge, ce
# qui est la seule facon de verifier que les migrations passent sur du neuf.

set -euo pipefail

NOM=automatecdc-base-de-test
IMAGE=postgres:16-alpine
PORT=${PORT_TEST:-55432}
BASE=automatecdc

MDP_SUPER=super-de-test
MDP_MIGRATION=migration-de-test
MDP_APP=app-de-test
MDP_LECTURE=lecture-de-test

RACINE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
INIT="$RACINE/infra/postgres/init/01-roles.sql"

demarrer() {
    if docker ps --format '{{.Names}}' | grep -qx "$NOM"; then
        echo "La base de test tourne deja."
        options
        return
    fi

    docker rm -f "$NOM" >/dev/null 2>&1 || true

    echo "Demarrage de $IMAGE sur le port $PORT..."
    docker run -d --name "$NOM" \
        -e POSTGRES_DB="$BASE" \
        -e POSTGRES_USER=postgres \
        -e POSTGRES_PASSWORD="$MDP_SUPER" \
        -e POSTGRES_INITDB_ARGS="--locale=C --encoding=UTF8" \
        -p "$PORT:5432" \
        "$IMAGE" >/dev/null

    printf "Attente de la base"
    for _ in $(seq 1 60); do
        if docker exec "$NOM" pg_isready -U postgres -d "$BASE" >/dev/null 2>&1; then
            echo " : prete."
            break
        fi
        printf "."
        sleep 1
    done

    # Le script d'amorcage est celui que l'image officielle executera sur le
    # VPS. Le rejouer ici par psql, avec les memes variables, teste le fichier
    # reel plutot qu'une copie qui pourrait diverger.
    #
    # Il entre par l'entree standard plutot que par docker cp : sous Git Bash,
    # docker reinterprete les chemins de style /c/... et ne retrouve plus le
    # fichier. L'entree standard ne traverse aucune conversion.
    docker exec -i \
        -e MDP_APP="$MDP_APP" \
        -e MDP_LECTURE="$MDP_LECTURE" \
        -e MDP_MIGRATION="$MDP_MIGRATION" \
        "$NOM" psql -v ON_ERROR_STOP=1 -U postgres -d "$BASE" < "$INIT" >/dev/null

    echo "Roles migration, app et lecture crees."
    options
}

options() {
    cat <<FIN

Pour lancer les tests d'integration :

  mvn test -Dtest.postgres.url=jdbc:postgresql://localhost:$PORT/$BASE \\
           -Dtest.postgres.mdp.app=$MDP_APP \\
           -Dtest.postgres.mdp.migration=$MDP_MIGRATION \\
           -Dtest.postgres.mdp.lecture=$MDP_LECTURE
FIN
}

arreter() {
    docker rm -f "$NOM" >/dev/null 2>&1 && echo "Base de test supprimee." \
        || echo "Aucune base de test a supprimer."
}

etat() {
    if docker ps --format '{{.Names}}' | grep -qx "$NOM"; then
        echo "La base de test tourne sur le port $PORT."
    else
        echo "La base de test ne tourne pas."
    fi
}

case "${1:-demarrer}" in
    demarrer) demarrer ;;
    arreter)  arreter ;;
    etat)     etat ;;
    *) echo "Usage : $0 [demarrer|arreter|etat]" >&2; exit 1 ;;
esac
