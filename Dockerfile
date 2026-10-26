### Construction
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Les dependances d'abord : une modification de source ne les retelecharge pas.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests \
 && mv target/*.jar /build/application.jar

### Execution
FROM eclipse-temurin:21-jre-jammy

# qpdf sert a la fusion de secours, curl au healthcheck, fontconfig au rendu
# Jasper (les polices embarquees ont besoin d'un cache de polices valide).
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl fontconfig qpdf \
 && rm -rf /var/lib/apt/lists/*

# Jamais root.
RUN useradd --system --uid 10001 --create-home --home-dir /home/offres offres \
 && mkdir -p /var/automatecdc/fichiers \
 && chown -R offres:offres /var/automatecdc

WORKDIR /app
COPY --from=build --chown=offres:offres /build/application.jar ./application.jar

USER offres
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
