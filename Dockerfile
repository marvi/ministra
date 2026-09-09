# syntax=docker/dockerfile:1
#
# Bygger en avbild av Ministra. Mönstret följer marvi/lektionarium (D-032).
#
# Ministra är en enda modul, så inget -pl/-am. Konfigurationen kommer uteslutande från
# miljövariabler ur en env-fil som quadleten pekar ut (D-014) — ingenting läses från en
# fil inuti avbilden.

# ---------- Bygg ----------
FROM docker.io/library/eclipse-temurin:25-jdk AS build

# Maven Wrapper väljer .tar.gz i stället för .zip när unzip saknas, och då stämmer inte
# den pinnade sha256-summan. Felmeddelandet påstår att distributionen kan vara
# komprometterad, men orsaken är bara att ett annat arkivformat hämtades.
RUN apt-get update \
 && apt-get install --yes --no-install-recommends unzip \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /build

# Pom-filen först. Beroendena hamnar då i ett eget lager som bara byggs om när pom
# ändras, inte vid varje kodändring.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B --no-transfer-progress dependency:go-offline -DskipTests

COPY src/ src/
# Testerna kräver Testcontainers och kör i CI, inte här.
RUN ./mvnw -B --no-transfer-progress package -DskipTests

# Dela upp jar-filen i lager. Beroenden ändras sällan och kan återanvändas mellan
# avbilder, medan applikationslagret är litet och byggs om ofta.
RUN java -Djarmode=tools -jar target/ministra-*.jar \
      extract --layers --launcher --destination /app

# ---------- Kör ----------
FROM docker.io/library/eclipse-temurin:25-jre

# image.source kopplar paketet till repot på GitHub. Utan den hamnar avbilden löst under
# kontot i stället för på projektsidan.
LABEL org.opencontainers.image.source="https://github.com/marvi/ministra" \
      org.opencontainers.image.description="Samlar in tillgänglighet inför söndagens gudstjänster"

# curl används av hälsokontrollen längre ned.
RUN apt-get update \
 && apt-get install --yes --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 10001 ministra \
 && useradd --system --uid 10001 --gid 10001 --home-dir /app --no-create-home ministra

WORKDIR /app

# Ordningen är den lagren ändras i: minst föränderligt först.
COPY --from=build --chown=10001:10001 /app/dependencies/ ./
COPY --from=build --chown=10001:10001 /app/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /app/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /app/application/ ./

USER 10001:10001
EXPOSE 8080

# JVM:en läser JAVA_TOOL_OPTIONS av sig själv, så starten kan ske utan skal.
# MaxRAMPercentage får den att rätta sig efter containerns minnesgräns i stället för
# efter värdens totala minne.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# Skalform, så att SERVER_PORT slår igenom om porten flyttas.
# start-period ger JVM:en tid att komma igång innan misslyckanden räknas.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS "http://localhost:${SERVER_PORT:-8080}/actuator/health" || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
