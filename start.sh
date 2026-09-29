docker compose down && \
  ./gradlew :app:bootJar -q && \
  docker rmi tracing-workshop-app:latest --force 2>/dev/null || true && \
  docker build -f docker/Dockerfile -t tracing-workshop-app:latest . && \
  docker compose up -d
