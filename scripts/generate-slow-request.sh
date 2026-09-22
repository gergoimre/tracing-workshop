#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${TRANSFER_URL:-http://localhost:8080}"
COUNT="${1:-3}"

echo "Generating ${COUNT} request(s) — EUR→BRL BANK_TRANSFER 15000"
echo "Target: ${BASE_URL}"
echo ""

for i in $(seq 1 "$COUNT"); do
  echo -n "  Request ${i}/${COUNT} ... "
  start=$SECONDS
  response=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/transfers/prepare" \
    -H "Content-Type: application/json" \
    -d '{"sourceCurrency":"EUR","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":15000,"recipientId":"rec-2"}')
  elapsed=$(( SECONDS - start ))
  status=$(echo "$response" | tail -1)
  if [ "$status" = "200" ]; then
    echo "OK — ~${elapsed}s"
  else
    echo "FAILED (HTTP ${status})"
  fi
done

echo ""
echo "Now open Grafana: http://localhost:3000"
echo "1. Open the Workshop — Trace Explorer dashboard, or go to Explore → Tempo"
echo "2. The slow trace will appear in the 'Slow traces' panel automatically"
echo "3. Click the Trace ID to open the waterfall"
