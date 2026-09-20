#!/usr/bin/env bash
set -euo pipefail

PASS=0
FAIL=0

check() {
  local label="$1"
  local url="$2"
  printf "  %-40s" "${label}"
  if curl -sf --max-time 5 "${url}" > /dev/null 2>&1; then
    echo "PASS"
    PASS=$(( PASS + 1 ))
  else
    echo "FAIL  (${url})"
    FAIL=$(( FAIL + 1 ))
  fi
}

echo ""
echo "Verifying workshop environment..."
echo ""

check "Transfer service  (health)"  "http://localhost:8080/actuator/health"
check "Pricing service   (health)"  "http://localhost:8081/actuator/health"
check "FX service        (health)"  "http://localhost:8082/actuator/health"
check "Support service   (health)"  "http://localhost:8083/actuator/health"
check "OTel Collector    (health)"  "http://localhost:13133"
check "Tempo             (ready)"   "http://localhost:3200/ready"
check "Grafana           (health)"  "http://localhost:3000/api/health"

echo ""
echo "Results: ${PASS} passed, ${FAIL} failed"

if [ "$FAIL" -gt 0 ]; then
  echo ""
  echo "Some services are not ready yet."
  echo "If you just ran 'docker compose up --build', wait a moment and retry."
  echo ""
  echo "Tip (Colima): make sure you started with enough resources:"
  echo "  colima start --cpu 4 --memory 8 --disk 40"
  exit 1
else
  echo ""
  echo "All services healthy!"
  echo ""
  echo "  API:     http://localhost:8080"
  echo "  Grafana: http://localhost:3000"
  echo ""
  echo "Next steps:"
  echo "  ./scripts/generate-traffic.sh       # fast requests"
  echo "  ./scripts/generate-slow-request.sh  # triggers the bug"
fi
