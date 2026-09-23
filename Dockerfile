# =====================================================================
# Multi-stage build : Maven builder + JRE runtime
# Avantages :
#   - Pas besoin de Maven/JDK sur le serveur, juste Docker.
#   - Image runtime ~250 MB (JRE) au lieu de ~450 MB (JDK).
#   - Cache des dépendances Maven (couche séparée du code source).
#   - Build reproductible sur n'importe quelle machine + CI.
# =====================================================================

# ---------- STAGE 1 : build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Couche 1 : copier juste pom.xml et télécharger les deps (cache friendly).
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Couche 2 : code source (invalidée à chaque modif source mais les deps
# restent en cache).
COPY src ./src

# Build : skip les tests (lance-les en CI séparément si besoin).
RUN mvn -B -q clean package -DskipTests \
 && mv target/*.jar target/app.jar

# ---------- STAGE 2 : runtime ----------
FROM eclipse-temurin:17-jre-jammy AS runtime

# OCI labels (visibles via `docker inspect`).
LABEL org.opencontainers.image.title="LSTracker" \
      org.opencontainers.image.description="Lab Sample Tracker — Spring Boot 3 + PostgreSQL" \
      org.opencontainers.image.vendor="I-TECH CIV" \
      org.opencontainers.image.source="https://github.com/<org>/labSampleTracker"

# Utilisateur non-root pour le runtime (sécurité).
RUN useradd --system --uid 1001 --create-home --shell /bin/bash app
WORKDIR /app

# Outils légers pour healthcheck + tz data.
RUN apt-get update \
 && apt-get install --no-install-recommends -y curl tzdata \
 && rm -rf /var/lib/apt/lists/*

COPY --from=build --chown=app:app /workspace/target/app.jar /app/app.jar

# Variables par défaut ; surchargées par docker-compose.
ENV TZ=Africa/Abidjan \
    SERVER_PORT=9200 \
    SPRING_PROFILES_ACTIVE=prod \
    MANAGEMENT_PORT=9300 \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 9200

USER app

# Healthcheck sur le port de gestion interne (actuator), jamais publié.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -f http://localhost:${MANAGEMENT_PORT}/actuator/health || exit 1

# `sh -c` pour expansion de $JAVA_OPTS.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
