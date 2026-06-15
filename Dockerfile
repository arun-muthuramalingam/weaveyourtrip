# syntax=docker/dockerfile:1.7
# =============================================================================
# WeaveYourTrip — production container
# Multi-stage so the final image holds just the JRE + JAR (no Maven, no source).
# =============================================================================

# ─── Stage 1: build ──────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy Maven wrapper + pom first so dependencies cache in a separate layer.
# Any source change re-runs only the package step, not the dep download.
COPY pom.xml mvnw ./
COPY .mvn ./.mvn
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

# Now copy source and build the JAR
COPY src ./src
RUN ./mvnw -B -DskipTests package


# ─── Stage 2: runtime ────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app

# Non-root user — Spring Boot doesn't need root, so don't run as root
RUN groupadd --system wyt && useradd --system --gid wyt --create-home wyt
USER wyt

# Copy just the fat jar from the build stage
COPY --from=builder --chown=wyt:wyt /app/target/weaveyourtrip-0.0.1-SNAPSHOT.jar app.jar

# Default profile when running inside Docker (talks to 'db' service)
ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8080

# Enable Java's container awareness + sensible heap defaults.
# Override at runtime via JAVA_OPTS env var if needed.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
