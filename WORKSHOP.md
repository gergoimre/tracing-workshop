# Workshop Exercises

This workshop has two independent investigations. Each teaches a different lesson
about why distributed tracing reveals problems that logs cannot.

---

## Setup

```bash
docker compose up --build
./scripts/verify-environment.sh
```

Generate a baseline of requests first:

```bash
./scripts/generate-traffic.sh
```

---

## Investigation 1 — The slow transfer

### Background

Most transfer requests complete in well under a second. A specific combination
of inputs takes roughly 3× longer. Users have noticed. Nobody can explain why —
the logs look clean and every service returns 200 OK.

### Step 1 — Reproduce the slow request

```bash
./scripts/generate-slow-request.sh
```

Note the elapsed time printed in the terminal.

```bash
curl -s -X POST http://localhost:8080/transfers/prepare \
  -H "Content-Type: application/json" \
  -d '{"sourceCurrency":"EUR","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":15000,"recipientId":"rec-2"}'
```

### Step 2 — Find slow traces in Grafana

1. Open http://localhost:3000
2. Open the **Workshop — Trace Explorer** dashboard, or run this TraceQL query in Explore:

```
{ resource.service.name = "transfer-service" && name = "POST /transfers/prepare" && duration > 2s }
```

### Step 3 — Identify the slow span

Open one slow trace. Expand the waterfall. There are many services — focus on the critical path.

- Which service contains the single longest span?
- Which span takes up most of the total duration?
- How many child spans does the slow parent span have?

### Step 4 — Compare span attributes

Look at the `pricing.calculate` span on a slow trace vs a fast one.

| Attribute | Slow trace | Fast trace |
|---|---|---|
| `pricing.strategy` | ? | ? |
| `pricing.route_candidate_count` | ? | ? |

What combination of `transfer.*` attributes is associated with the slow traces?

### Step 5 — Examine the FX spans

In the slow trace, look at the child spans of `pricing.calculate`.

- How many `fx.call` spans are there?
- Do they overlap in time, or do they start one after another?

```
Slow (actual):
  fx.call [provider-a]  ───────────
  fx.call [provider-b]              ───────────
  fx.call [provider-c]                          ───────────

Expected:
  fx.call [provider-a]  ───────────
  fx.call [provider-b]  ───────────
  fx.call [provider-c]  ───────────
```

### Step 6 — Find the bug in the code

Open `app/src/main/kotlin/com/workshop/pricing/PricingController.kt`.

Look at the section that fetches FX rates for MULTI_ROUTE candidates.

**The bug:** plain sequential `map` — the calls could run in parallel but run one after another.

```kotlin
val rates = candidates.map { candidate ->
    fxClient.getRate(request.sourceCurrency, request.targetCurrency, candidate)
}
```

### Step 7 — Fix the code

```kotlin
val rates = candidates.parallelStream()
    .map { fxClient.getRate(request.sourceCurrency, request.targetCurrency, it) }
    .toList()
```

### Step 8 — Enable and run the regression test

In `app/src/test/kotlin/com/workshop/pricing/PricingConcurrencyTest.kt`:

1. Remove the `@Disabled` annotation.
2. Run: `./gradlew :app:test --tests "com.workshop.pricing.PricingConcurrencyTest"`
3. The test should now pass.

### Step 9 — Rebuild and verify

```bash
./gradlew :app:bootJar -q
docker compose up -d --build pricing
./scripts/generate-slow-request.sh
```

Open Grafana and compare. The three `fx.call` spans should now overlap.
Total duration drops from ~3 s to ~1 s.

---

## Investigation 2 — The silent screening storm

### Background

The risk service screens transfers against a watchlist. Most requests go through
risk in under 50ms with no issues — a single clean span in the trace. But
under a specific combination of inputs that only some clients send, requests
to the risk service take significantly longer, and the upstream transfer slows
with them. There are no errors, no warnings, and nothing unusual in any log.

### Step 1 — Generate the triggering request

Standard traffic does not trigger this bug. You need a request with three
conditions all true at the same time:

```bash
./scripts/generate-screening-request.sh
```

Or manually:

```bash
curl -s -X POST http://localhost:8080/transfers/prepare \
  -H "Content-Type: application/json" \
  -d '{
    "sourceCurrency": "GBP",
    "targetCurrency": "USD",
    "transferType": "BANK_TRANSFER",
    "amount": 12000,
    "recipientId": "rec-1",
    "memo": "urgent payment, urgent transfer, payment reference"
  }'
```

Compare the elapsed time to a standard GBP→USD request without a memo.

### Step 2 — Find the trace in Grafana

Use this TraceQL query in Explore → Tempo:

```
{ resource.service.name = "risk-service" && name = "risk.score" && span.risk.screening_terms_checked > 0 }
```

Or find the slow `POST /transfers/prepare` trace and expand the waterfall.

### Step 3 — Examine the risk.score span

Expand the `risk.score` span inside the trace.

**Questions to answer:**
- How many child spans does `risk.score` have?
- How many of them are `POST /screening/check`?
- Do the screening spans overlap, or are they sequential?
- What does the span attribute `risk.screening_terms_checked` tell you?

Compare with a trace from a request **without** a memo — `risk.score` should have
only one child span (`POST /device/fingerprint`).

### Step 4 — Why logs miss this

Look at the logs for the risk-service in Grafana → Loki:

```
{service_name="risk-service"}
```

- Every `POST /screening/check` returns 200 OK.
- Each individual screening call takes ~10ms — completely normal.
- The log lines for each call look identical and unremarkable.
- Nothing says "this ran 6 times instead of 2."

To detect excessive screening calls from logs alone you would need to: correlate
all screening log lines by `trace_id`, group them, count per-trace, and compare
counts across requests. In a trace the excess is immediately visible: the
waterfall has a staircase of identical spans that simply should not be there.

### Step 5 — Identify the three trigger conditions

Look at `app/src/main/kotlin/com/workshop/risk/RiskController.kt`.

Find the condition that gates the memo screening path. What are the three
values that must all be true simultaneously?

- Condition 1: ?
- Condition 2: ?
- Condition 3: ?

Why does standard traffic never hit this path? Which condition is almost
never present in normal requests?

### Step 6 — Find the bug in the code

Look at `screenMemoTerms()` inside `RiskController`.

**The bug:** the memo is split by comma and then by space, and the resulting
terms are used directly without deduplication or blank filtering.

Given memo `"urgent payment, urgent transfer, payment reference"`:
- Split by `,` → `["urgent payment", " urgent transfer", " payment reference"]`
- Then split each by ` ` → `["urgent", "payment", "", "urgent", "transfer", "", "payment", "reference"]`
- After `.trim().lowercase()` → `["urgent", "payment", "", "urgent", "transfer", "", "payment", "reference"]`
- Result: 8 terms, with duplicates and empty strings → 8 sequential screening calls

The empty strings and duplicates are pure waste. Each one triggers a full
round-trip to the screening service.

### Step 7 — Fix the code

Add `.filter { it.isNotBlank() }.distinct()` before iterating:

```kotlin
private fun screenMemoTerms(memo: String, customerId: String) {
    val terms = memo.split(",")
        .flatMap { it.split(" ") }
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .distinct()

    terms.forEach { term ->
        val result = screeningClient.check(term, customerId)
        log.info("Screening check: term={} flagged={}", term, result.flagged)
    }

    Span.current().setAttribute("risk.screening_terms_checked", terms.size.toLong())
}
```

For the same memo this now produces: `["urgent", "payment", "transfer", "reference"]` → 4 calls,
all distinct and meaningful.

### Step 8 — Enable and run the regression test

In `app/src/test/kotlin/com/workshop/risk/RiskScreeningTest.kt`:

1. Remove the `@Disabled` annotation.
2. Run: `./gradlew :app:test --tests "com.workshop.risk.RiskScreeningTest"`
3. The test should now pass — exactly 2 distinct terms screened.

### Step 9 — Rebuild and verify

```bash
./gradlew :app:bootJar -q
docker compose up -d --build risk
./scripts/generate-screening-request.sh
```

Open the new trace. The `risk.score` span should now have far fewer
`screening.check` children, and the total duration should drop noticeably.

---

## Reflection

| | Bug #1 (sequential FX) | Bug #2 (screening fan-out) |
|---|---|---|
| Visible in logs | No | No — each call is 200 OK ~10ms |
| Error or exception | No | No |
| Trigger | EUR→BRL 10000+ always | `memo` set + USD + 10000+ (rare combo) |
| Visible in traces | Yes — sequential fx waterfall | Yes — staircase of duplicate screening spans |
| Hidden in code because | `map{}` looks fine | Looks like legitimate per-term screening |
| Fix | `parallelStream()` | `.filter { isNotBlank() }.distinct()` |

Both bugs are invisible to every observability signal except the trace structure.
Bug #2 is additionally hidden by a rare trigger — most traces never show the problem at all.
