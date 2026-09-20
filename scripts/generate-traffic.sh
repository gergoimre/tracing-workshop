#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${TRANSFER_URL:-http://localhost:8080}"
ROUNDS="${1:-3}"

echo "Generating mixed traffic — ${ROUNDS} round(s) of fast requests"
echo "Target: ${BASE_URL}"
echo ""

fast_request() {
  local label="$1"
  local body="$2"
  echo -n "  ${label} ... "
  response=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/transfers/prepare" \
    -H "Content-Type: application/json" \
    -d "${body}")
  status=$(echo "$response" | tail -1)
  body_out=$(echo "$response" | head -1)
  if [ "$status" = "200" ]; then
    strategy=$(echo "$body_out" | grep -o '"pricingStrategy":"[^"]*"' | cut -d'"' -f4)
    echo "OK (strategy=${strategy})"
  else
    echo "FAILED (HTTP ${status})"
  fi
}

for i in $(seq 1 "$ROUNDS"); do
  echo "Round ${i}/${ROUNDS}:"

  fast_request "EUR→USD 15000 (fast)" \
    '{"sourceCurrency":"EUR","targetCurrency":"USD","transferType":"BANK_TRANSFER","amount":15000}'

  fast_request "EUR→BRL 500  (fast, small amount)" \
    '{"sourceCurrency":"EUR","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":500}'

  fast_request "GBP→BRL 15000 (fast, wrong source)" \
    '{"sourceCurrency":"GBP","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":15000}'

  echo ""
done

echo "Done. Open Grafana at http://localhost:3000 and explore the traces."
echo "Hint: compare these fast traces with the slow one from generate-slow-request.sh"
