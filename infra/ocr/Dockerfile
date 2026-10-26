FROM python:3.12-slim-bookworm

# Tesseract en francais : c'est la langue de tous les DAO et de toutes les
# attestations que la plateforme recoit.
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      tesseract-ocr \
      tesseract-ocr-fra \
      tesseract-ocr-osd \
      ghostscript \
      qpdf \
      unpaper \
      pngquant \
      poppler-utils \
 && rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir "ocrmypdf==16.10.0"

RUN useradd --system --uid 10002 --create-home --home-dir /home/ocr ocr

COPY serveur.py /app/serveur.py

USER ocr
WORKDIR /app
EXPOSE 5000
ENTRYPOINT ["python", "-u", "/app/serveur.py"]
