"""Enveloppe HTTP minimale autour d'ocrmypdf.

ocrmypdf est un outil en ligne de commande ; l'application Spring Boot, elle,
parle HTTP. Ce serveur fait le pont, et rien d'autre : il n'est joignable que
depuis le reseau Docker interne.

    POST /ocr          corps = PDF ou image brute   -> PDF avec couche texte
    POST /texte        corps = PDF ou image brute   -> texte brut (UTF-8)
    GET  /sante                                     -> ok

Les documents passent par des fichiers temporaires effaces dans tous les cas,
y compris en erreur : rien d'un DAO ne doit survivre a la requete.
"""

import os
import shutil
import subprocess
import tempfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("PORT", "5000"))
TAILLE_MAX = int(os.environ.get("TAILLE_MAX_OCTETS", str(256 * 1024 * 1024)))
DELAI = int(os.environ.get("DELAI_SECONDES", "600"))

# Signatures de debut de fichier, et l'extension qui va avec. Ecrites en
# valeurs numeriques plutot qu'en chaines echappees : ce fichier traverse des
# outils qui n'ont pas tous la meme idee de ce qu'est un antislash.
SIGNATURES = [
    (bytes([0x25, 0x50, 0x44, 0x46]), ".pdf"),   # %PDF
    (bytes([0xFF, 0xD8, 0xFF]), ".jpg"),         # JPEG
    (bytes([0x89, 0x50, 0x4E, 0x47]), ".png"),   # PNG
    (bytes([0x49, 0x49, 0x2A, 0x00]), ".tif"),   # TIFF petit-boutiste
    (bytes([0x4D, 0x4D, 0x00, 0x2A]), ".tif"),   # TIFF gros-boutiste
]


def _extension(debut):
    """Devine le type reel a partir des premiers octets.

    Le coffre recoit aussi bien des PDF que des photos prises au telephone.
    ocrmypdf choisit son traitement d'apres l'extension du fichier : lui donner
    un .pdf qui contient un JPEG le fait echouer sur un message obscur. On
    regarde donc le contenu, et non le nom que le client a envoye - lequel
    vient de l'exterieur et ne se croit pas.
    """
    for signature, extension in SIGNATURES:
        if debut.startswith(signature):
            return extension
    return ".pdf"


def _lancer(commande, delai=DELAI):
    return subprocess.run(
        commande,
        capture_output=True,
        timeout=delai,
        check=False,
    )


class Gestionnaire(BaseHTTPRequestHandler):
    server_version = "ocr-automatecdc/1.0"
    protocol_version = "HTTP/1.1"

    def _repondre(self, code, corps, type_contenu="text/plain; charset=utf-8"):
        self.send_response(code)
        self.send_header("Content-Type", type_contenu)
        self.send_header("Content-Length", str(len(corps)))
        self.end_headers()
        self.wfile.write(corps)

    def do_GET(self):
        if self.path == "/sante":
            self._repondre(200, b"ok")
        else:
            self._repondre(404, b"chemin inconnu")

    def do_POST(self):
        if self.path not in ("/ocr", "/texte"):
            self._repondre(404, b"chemin inconnu")
            return

        taille = int(self.headers.get("Content-Length") or 0)
        if taille <= 0:
            self._repondre(400, "Corps vide : envoyez le document brut.".encode("utf-8"))
            return
        if taille > TAILLE_MAX:
            self._repondre(413, "Document trop volumineux.".encode("utf-8"))
            return

        dossier = tempfile.mkdtemp(prefix="ocr-")
        try:
            brut = os.path.join(dossier, "recu")
            sortie = os.path.join(dossier, "sortie.pdf")

            with open(brut, "wb") as f:
                restant = taille
                while restant > 0:
                    morceau = self.rfile.read(min(1 << 16, restant))
                    if not morceau:
                        break
                    f.write(morceau)
                    restant -= len(morceau)

            with open(brut, "rb") as f:
                entree = brut + _extension(f.read(16))
            os.rename(brut, entree)

            # --skip-text laisse passer un PDF deja textuel sans le reimprimer :
            # un DAO natif ne doit pas perdre en qualite au passage.
            commande = [
                "ocrmypdf",
                "--language", "fra",
                "--skip-text",
                "--rotate-pages",
                "--deskew",
                "--optimize", "1",
                "--quiet",
            ]
            # Une photo ne declare pas sa resolution : sans cette indication,
            # ocrmypdf refuse de traiter l'image plutot que de deviner.
            if not entree.endswith(".pdf"):
                commande = commande + ["--image-dpi", "300"]

            resultat = _lancer(commande + [entree, sortie])
            if resultat.returncode != 0:
                message = resultat.stderr.decode("utf-8", "replace")[:2000]
                self._repondre(502, ("Echec ocrmypdf : " + message).encode("utf-8"))
                return

            if self.path == "/ocr":
                with open(sortie, "rb") as f:
                    self._repondre(200, f.read(), "application/pdf")
                return

            extraction = _lancer(["pdftotext", "-layout", "-enc", "UTF-8", sortie, "-"])
            if extraction.returncode != 0:
                message = extraction.stderr.decode("utf-8", "replace")[:2000]
                self._repondre(502, ("Echec pdftotext : " + message).encode("utf-8"))
                return
            self._repondre(200, extraction.stdout, "text/plain; charset=utf-8")

        except subprocess.TimeoutExpired:
            self._repondre(504, "Depassement du delai de traitement.".encode("utf-8"))
        except Exception as erreur:  # noqa: BLE001 - le detail part au client interne
            self._repondre(500, str(erreur).encode("utf-8"))
        finally:
            shutil.rmtree(dossier, ignore_errors=True)

    def log_message(self, format, *args):
        print("%s - %s" % (self.address_string(), format % args))


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", PORT), Gestionnaire).serve_forever()
