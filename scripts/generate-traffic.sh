#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${TRANSFER_URL:-http://localhost:8080}"
COUNT="${1:-12}"

# Requests that will always be sent (to ensure interesting traces appear)
GUARANTEED=(
  '{"sourceCurrency":"EUR","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":15000,"recipientId":"rec-2"}'
  '{"sourceCurrency":"EUR","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":12000,"recipientId":"rec-1"}'
  '{"sourceCurrency":"GBP","targetCurrency":"USD","transferType":"BANK_TRANSFER","amount":12000,"recipientId":"rec-1","memo":"urgent payment, urgent transfer, payment reference"}'
)

# Background noise — random mix of normal-looking requests
POOL=(
  '{"sourceCurrency":"EUR","targetCurrency":"USD","transferType":"BANK_TRANSFER","amount":15000,"recipientId":"rec-1"}'
  '{"sourceCurrency":"EUR","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":500,"recipientId":"rec-2"}'
  '{"sourceCurrency":"GBP","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":15000,"recipientId":"rec-1"}'
  '{"sourceCurrency":"GBP","targetCurrency":"USD","transferType":"BANK_TRANSFER","amount":8000,"recipientId":"rec-3"}'
  '{"sourceCurrency":"EUR","targetCurrency":"USD","transferType":"BANK_TRANSFER","amount":500,"recipientId":"rec-2"}'
  '{"sourceCurrency":"GBP","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":300,"recipientId":"rec-1"}'
  '{"sourceCurrency":"EUR","targetCurrency":"USD","transferType":"BANK_TRANSFER","amount":25000,"recipientId":"rec-3"}'
  '{"sourceCurrency":"GBP","targetCurrency":"USD","transferType":"BANK_TRANSFER","amount":12000,"recipientId":"rec-1"}'
  '{"sourceCurrency":"EUR","targetCurrency":"USD","transferType":"BANK_TRANSFER","amount":8500,"recipientId":"rec-2"}'
  '{"sourceCurrency":"GBP","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":200,"recipientId":"rec-3"}'
)
POOL_SIZE=${#POOL[@]}

echo "Sending $((${#GUARANTEED[@]} + COUNT)) requests to ${BASE_URL}"
echo ""

TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

send_request() {
  local i="$1"
  local payload="$2"

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
    echo "  ${src}→${tgt} ${amt} — OK (~${elapsed}s)" > "${TMPDIR}/req_${i}"
  else
    echo "  ${src}→${tgt} ${amt} — FAILED (HTTP ${http_code})" > "${TMPDIR}/req_${i}"
  fi
}

# Send guaranteed requests first
for i in "${!GUARANTEED[@]}"; do
  send_request "g${i}" "${GUARANTEED[$i]}" &
done

# Fill the rest with random picks from the pool
for i in $(seq 0 "$COUNT"); do
  idx=$(( RANDOM % POOL_SIZE ))
  send_request "$i" "${POOL[$idx]}" &
done

wait

# Print results
for i in "${!GUARANTEED[@]}"; do
  [ -f "${TMPDIR}/req_g${i}" ] && cat "${TMPDIR}/req_g${i}"
done
for i in $(seq 0 "$COUNT"); do
  [ -f "${TMPDIR}/req_${i}" ] && cat "${TMPDIR}/req_${i}"
done

echo ""
echo "Done. Open Grafana: http://localhost:3000"
