# ── Runtime image ─────────────────────────────────────────────────────────────
# The jar is built on the host via: ./gradlew :app:bootJar
# This keeps the Docker build fast and avoids JVM memory pressure inside the
# Colima VM during the build stage.
#
# Tracing is provided by Micrometer Tracing (micrometer-tracing-bridge-otel)
# wired up inside the Spring Boot app — no Java agent required.
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

COPY app/build/libs/app-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Xms128m", "-Xmx384m", \
  "-jar", "app.jar"]
