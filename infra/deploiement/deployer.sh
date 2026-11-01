#!/usr/bin/env bash
#
# Deploiement sur le VPS. A executer SUR le serveur, depuis la racine du
# depot.
#
#   ./infra/deploiement/deployer.sh
#
# Le script ne fabrique rien et ne devine rien : il verifie que le .env est
# complet et coherent, puis construit et demarre. Un deploiement qui part sur
# une configuration a moitie remplie se remarque une semaine plus tard, quand
# un lien signe ne s'ouvre pas ou qu'un courriel ne part pas.

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

rouge()  { printf '\033[31m%s\033[0m\n' "$*"; }
vert()   { printf '\033[32m%s\033[0m\n' "$*"; }
jaune()  { printf '\033[33m%s\033[0m\n' "$*"; }

# ------------------------------------------------------------------ le .env

if [ ! -f .env ]; then
    rouge "Aucun fichier .env."
    echo "  cp .env.example .env    puis remplissez chaque ligne."
    echo "  Les secrets se generent avec : openssl rand -base64 36"
    exit 1
fi

set -a
# shellcheck disable=SC1091
. ./.env
set +a

manquants=()
for variable in POSTGRES_DB MDP_SUPERUSER MDP_MIGRATION MDP_APP MDP_LECTURE \
                CLE_LIENS_SIGNES CLE_CHIFFREMENT_FICHIERS; do
    if [ -z "${!variable:-}" ]; then
        manquants+=("$variable")
    fi
done

if [ ${#manquants[@]} -gt 0 ]; then
    rouge "Le .env est incomplet. Manquent : ${manquants[*]}"
    exit 1
fi

# ------------------------------------------------- domaine ou IP, et HTTPS

if [ -z "${DOMAINE:-}" ]; then
    rouge "DOMAINE est vide. Mettez-y un nom de domaine, ou l'IP du serveur."
    exit 1
fi

# Une IP nue ne peut pas recevoir de certificat Let's Encrypt. Plutot que de
# laisser Caddy echouer en boucle sur une demande impossible, on bascule sur
# la variante sans TLS - et on le dit, parce que ce n'est pas anodin.
if printf '%s' "$DOMAINE" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'; then
    export CADDYFILE=./infra/caddy/Caddyfile.ip
    export URL_PUBLIQUE="http://$DOMAINE"
    export COOKIE_SECURE=false
    jaune "=============================================================="
    jaune " DEPLOIEMENT SANS HTTPS"
    jaune "=============================================================="
    jaune " $DOMAINE est une adresse IP. Let's Encrypt ne delivre pas de"
    jaune " certificat pour une IP : tout circulera en clair, mots de"
    jaune " passe compris, et le cookie de session ne sera pas protege."
    jaune ""
    jaune " Bon pour montrer et pour tester. Pas pour y deposer un vrai"
    jaune " dossier. Des qu'un domaine pointe vers ce serveur, remettez-le"
    jaune " dans DOMAINE et relancez : le HTTPS s'obtient tout seul."
    jaune "=============================================================="
    echo
else
    export CADDYFILE=./infra/caddy/Caddyfile
    export URL_PUBLIQUE="https://$DOMAINE"
    export COOKIE_SECURE=true
    if [ -z "${EMAIL_ACME:-}" ]; then
        rouge "EMAIL_ACME est vide : Let's Encrypt refusera la demande."
        exit 1
    fi
    # Un domaine qui ne pointe pas encore ici fait echouer le certificat, et
    # le site reste inaccessible sans que rien ne l'explique.
    attendue=$(getent hosts "$DOMAINE" 2>/dev/null | awk '{print $1}' | head -1 || true)
    publique=$(curl -s -m 10 https://api.ipify.org 2>/dev/null || true)
    if [ -n "$attendue" ] && [ -n "$publique" ] && [ "$attendue" != "$publique" ]; then
        rouge "$DOMAINE pointe vers $attendue, mais ce serveur est $publique."
        rouge "Le certificat echouera. Corrigez l'enregistrement A d'abord."
        exit 1
    fi
fi

# ----------------------------------------------- l'IA et le courrier, dits

[ -z "${ANTHROPIC_API_KEY:-}" ] && \
    jaune "ANTHROPIC_API_KEY vide : l'extraction automatique des DAO sera inactive."
[ -z "${SMTP_HOTE:-}" ] && \
    jaune "SMTP_HOTE vide : aucun courriel ne partira (invitations, alertes)."

# ------------------------------------------------------------- la sauvegarde

if [ ! -f .cle-sauvegardee ]; then
    jaune "--------------------------------------------------------------"
    jaune " CLE_CHIFFREMENT_FICHIERS est IRREMPLACABLE."
    jaune " Les pieces et les PDF archives sont chiffres avec elle."
    jaune " Perdue, plus rien n'est relisible - aucune recuperation."
    jaune " Copiez-la AILLEURS QUE SUR CE SERVEUR, puis :"
    jaune "   touch .cle-sauvegardee"
    jaune " pour ne plus voir ce rappel."
    jaune "--------------------------------------------------------------"
    echo
fi

# ----------------------------------------------------------- on y va

vert "Construction et demarrage..."
docker compose up -d --build

echo
vert "Etat des conteneurs :"
docker compose ps

echo
vert "Attente de l'application (la compilation des modeles prend ~30 s)..."
for _ in $(seq 1 60); do
    if docker compose exec -T app curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
        vert "L'application repond."
        echo
        vert "Ouvrez : $URL_PUBLIQUE"
        exit 0
    fi
    sleep 5
done

rouge "L'application n'a pas repondu en cinq minutes."
rouge "Journal :  docker compose logs --tail=80 app"
exit 1
