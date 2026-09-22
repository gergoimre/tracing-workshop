#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${TRANSFER_URL:-http://localhost:8080}"
COUNT="${1:-12}"   # total requests to send, default 12

# ── Payload pool ──────────────────────────────────────────────────────────────
# Mix of fast and slow requests. The slow one is included so participants
# discover it naturally — they are NOT labelled.
PAYLOADS=(
  '{"sourceCurrency":"EUR","targetCurrency":"USD","transferType":"BANK_TRANSFER","amount":15000}'
  '{"sourceCurrency":"EUR","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":500}'
  '{"sourceCurrency":"GBP","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":15000}'
  '{"sourceCurrency":"GBP","targetCurrency":"USD","transferType":"BANK_TRANSFER","amount":8000}'
  '{"sourceCurrency":"EUR","targetCurrency":"USD","transferType":"BANK_TRANSFER","amount":500}'
  '{"sourceCurrency":"GBP","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":300}'
  '{"sourceCurrency":"EUR","targetCurrency":"USD","transferType":"BANK_TRANSFER","amount":25000}'
  '{"sourceCurrency":"EUR","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":15000}'
)
POOL_SIZE=${#PAYLOADS[@]}

echo "Sending ${COUNT} concurrent requests to ${BASE_URL}"
echo ""

# ── Temporary directory for results ──────────────────────────────────────────
TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

# ── Fire all requests concurrently ───────────────────────────────────────────
for i in $(seq 1 "$COUNT"); do
  # Pick a random payload
  idx=$(( RANDOM % POOL_SIZE ))
  payload="${PAYLOADS[$idx]}"

  (
    start=$SECONDS
    http_code=$(curl -s -o /dev/null -w "%{http_code}" \
      -X POST "${BASE_URL}/transfers/prepare" \
      -H "Content-Type: application/json" \
      -d "${payload}")
    elapsed=$(( SECONDS - start ))

    src=$(echo "$payload" | grep -o '"sourceCurrency":"[^"]*"' | cut -d'"' -f4)
    tgt=$(echo "$payload" | grep -o '"targetCurrency":"[^"]*"' | cut -d'"' -f4)
    amt=$(echo "$payload" | grep -o '"amount":[0-9]*' | cut -d: -f2)

    if [ "$http_code" = "200" ]; then
      echo "${src}→${tgt} ${amt} — OK (~${elapsed}s)" > "${TMPDIR}/req_${i}"
    else
      echo "${src}→${tgt} ${amt} — FAILED (HTTP ${http_code})" > "${TMPDIR}/req_${i}"
    fi
  ) &
done

# ── Wait for all background jobs ─────────────────────────────────────────────
wait

# ── Print results in order ───────────────────────────────────────────────────
for i in $(seq 1 "$COUNT"); do
  result_file="${TMPDIR}/req_${i}"
  if [ -f "$result_file" ]; then
    echo "  $(cat "$result_file")"
  fi
done

echo ""
echo "Done. Open Grafana: http://localhost:3000"
echo "Go to the Workshop dashboard and look for requests that took longer than the others."
