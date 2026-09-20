# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# Copy Gradle wrapper and build files first (layer-cached until deps change)
COPY gradlew .
COPY gradle/ gradle/
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY app/build.gradle.kts app/

# Download dependencies into cache layer before copying source
RUN ./gradlew :app:dependencies --no-daemon -q || true

# Copy source and build
COPY app/src app/src
RUN ./gradlew :app:bootJar --no-daemon -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# Vendored OTel Java agent (no network required at build time)
COPY docker/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar

COPY --from=build /workspace/app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-javaagent:/otel/opentelemetry-javaagent.jar", \
  "-jar", "app.jar"]
