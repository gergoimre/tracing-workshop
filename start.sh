#!/usr/bin/env bash
# Convenience script: build the jar locally then start docker compose.
# Usage: ./start.sh [--build | --up-only]
set -euo pipefail

BUILD=true
if [ "${1:-}" = "--up-only" ]; then
  BUILD=false
fi

if [ "$BUILD" = "true" ]; then
  echo "Building application jar..."
  ./gradlew :app:bootJar -q
  echo "Jar built."
fi

echo "Starting docker compose..."
docker compose up --build "$@"
