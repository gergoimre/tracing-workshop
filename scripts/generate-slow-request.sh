#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${TRANSFER_URL:-http://localhost:8080}"
COUNT="${1:-3}"

echo "Generating ${COUNT} slow request(s) — EUR→BRL BANK_TRANSFER 15000"
echo "Target: ${BASE_URL}"
echo "This request activates the MULTI_ROUTE pricing strategy."
echo ""

for i in $(seq 1 "$COUNT"); do
  echo -n "  Request ${i}/${COUNT} ... "
  start=$SECONDS
  response=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/transfers/prepare" \
    -H "Content-Type: application/json" \
    -d '{"sourceCurrency":"EUR","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":15000}')
  elapsed=$(( SECONDS - start ))
  status=$(echo "$response" | tail -1)
  body_out=$(echo "$response" | head -1)
  if [ "$status" = "200" ]; then
    strategy=$(echo "$body_out" | grep -o '"pricingStrategy":"[^"]*"' | cut -d'"' -f4)
    echo "OK — ~${elapsed}s (strategy=${strategy})"
  else
    echo "FAILED (HTTP ${status})"
  fi
done

echo ""
echo "Now open Grafana: http://localhost:3000"
echo "1. Go to Explore → Tempo"
echo "2. Run query: { duration > 2s }"
echo "3. Open a slow trace and examine the waterfall"
