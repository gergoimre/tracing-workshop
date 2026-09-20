# ── Runtime image ─────────────────────────────────────────────────────────────
# The jar is built on the host via: ./gradlew :app:bootJar
# This keeps the Docker build fast and avoids JVM memory pressure inside the
# Colima VM during the build stage.
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# Vendored OTel Java agent (no network required at build time)
COPY docker/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar

COPY app/build/libs/app-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-javaagent:/otel/opentelemetry-javaagent.jar", \
  "-Xms128m", "-Xmx384m", \
  "-jar", "app.jar"]
