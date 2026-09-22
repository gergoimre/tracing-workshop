#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${TRANSFER_URL:-http://localhost:8080}"
COUNT="${1:-12}"

PAYLOADS=(
  '{"sourceCurrency":"EUR","targetCurrency":"USD","transferType":"BANK_TRANSFER","amount":15000,"recipientId":"rec-1"}'
  '{"sourceCurrency":"EUR","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":500,"recipientId":"rec-2"}'
  '{"sourceCurrency":"GBP","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":15000,"recipientId":"rec-1"}'
  '{"sourceCurrency":"GBP","targetCurrency":"USD","transferType":"BANK_TRANSFER","amount":8000,"recipientId":"rec-3"}'
  '{"sourceCurrency":"EUR","targetCurrency":"USD","transferType":"BANK_TRANSFER","amount":500,"recipientId":"rec-2"}'
  '{"sourceCurrency":"GBP","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":300,"recipientId":"rec-1"}'
  '{"sourceCurrency":"EUR","targetCurrency":"USD","transferType":"BANK_TRANSFER","amount":25000,"recipientId":"rec-3"}'
  '{"sourceCurrency":"EUR","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":15000,"recipientId":"rec-2"}'
)
POOL_SIZE=${#PAYLOADS[@]}

echo "Sending ${COUNT} concurrent requests to ${BASE_URL}"
echo ""

TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

for i in $(seq 1 "$COUNT"); do
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

wait

for i in $(seq 1 "$COUNT"); do
  [ -f "${TMPDIR}/req_${i}" ] && echo "  $(cat "${TMPDIR}/req_${i}")"
done

echo ""
echo "Done. Open Grafana: http://localhost:3000"
echo "Go to the Workshop dashboard and look for requests that took longer than the others."
