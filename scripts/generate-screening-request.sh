#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo ""
echo "Sending memo+USD+10000 request to trigger Bug #2 (risk screening fan-out)..."
echo ""

START=$(date +%s)

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/transfers/prepare" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceCurrency": "GBP",
    "targetCurrency": "USD",
    "transferType": "BANK_TRANSFER",
    "amount": 12000,
    "recipientId": "rec-1",
    "memo": "urgent payment, urgent transfer, payment reference"
  }')

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | head -1)

END=$(date +%s)
ELAPSED=$(( (END - START) * 1000 ))

if [ "$HTTP_CODE" = "200" ]; then
  echo "  GBP→USD 12000 with memo — OK (~${ELAPSED}ms)"
else
  echo "  GBP→USD 12000 with memo — FAILED (HTTP $HTTP_CODE)"
  echo "  $BODY"
  exit 1
fi

echo ""
echo "Open Grafana: http://localhost:3000"
echo "Look for the risk-service span in this trace."
echo "Expand it — you should see multiple sequential screening.check spans."
echo ""
echo "TraceQL query to find this trace:"
echo "  { resource.service.name = \"risk-service\" && name = \"risk.score\" && span.risk.screening_terms_checked > 0 }"
echo ""
