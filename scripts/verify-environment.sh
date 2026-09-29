#!/usr/bin/env bash
set -euo pipefail

PASS=0
FAIL=0

check() {
  local label="$1"
  local url="$2"
  printf "  %-42s" "${label}"
  if curl -s --max-time 5 --output /dev/null "${url}" 2>/dev/null; then
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
echo "── Application services ──────────────────────────"
check "transfer    :8080  (health)"  "http://localhost:8080/actuator/health"
check "pricing     :8081  (health)"  "http://localhost:8081/actuator/health"
check "fx          :8082  (health)"  "http://localhost:8082/actuator/health"
check "support     :8083  (health)"  "http://localhost:8083/actuator/health"
check "auth        :8084  (health)"  "http://localhost:8084/actuator/health"
check "session     :8085  (health)"  "http://localhost:8085/actuator/health"
check "risk        :8086  (health)"  "http://localhost:8086/actuator/health"
check "device      :8087  (health)"  "http://localhost:8087/actuator/health"
check "ledger      :8088  (health)"  "http://localhost:8088/actuator/health"
check "accounts    :8089  (health)"  "http://localhost:8089/actuator/health"
check "notification :8090  (health)" "http://localhost:8090/actuator/health"
check "audit       :8091  (health)"  "http://localhost:8091/actuator/health"
check "screening   :8092  (health)"  "http://localhost:8092/actuator/health"
echo ""
echo "── Observability stack ───────────────────────────"
check "OTel Collector  :4318  (OTLP)" "http://localhost:4318"
check "Tempo           :3200  (ready)" "http://localhost:3200/ready"
check "Grafana         :3000  (health)" "http://localhost:3000/api/health"

echo ""
echo "Results: ${PASS} passed, ${FAIL} failed"

if [ "$FAIL" -gt 0 ]; then
  echo ""
  echo "Some services are not ready yet."
  echo "If you just ran './start.sh', wait a moment and retry."
  echo ""
  echo "Tip (Colima): make sure you started with enough resources:"
  echo "  colima start --cpu 4 --memory 8 --disk 40"
  exit 1
else
  echo ""
  echo "All 16 services healthy!"
  echo ""
  echo "  API:     http://localhost:8080"
  echo "  Grafana: http://localhost:3000"
  echo ""
  echo "Next step:"
  echo "  ./scripts/generate-traffic.sh   # generate a mix of requests"
fi
