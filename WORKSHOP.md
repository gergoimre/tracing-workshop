# Workshop Exercises

This workshop has two independent investigations. Each teaches a different lesson
about why distributed tracing reveals problems that logs cannot.

---

## Setup

```bash
docker compose up --build
./scripts/verify-environment.sh
```

Generate a baseline of fast requests first:

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
# Or run it manually:
curl -s -X POST http://localhost:8080/transfers/prepare \
  -H "Content-Type: application/json" \
  -d '{"sourceCurrency":"EUR","targetCurrency":"BRL","transferType":"BANK_TRANSFER","amount":15000}'
```

### Step 2 — Find slow traces in Grafana

1. Open http://localhost:3000
2. Go to **Explore** → select **Tempo**
3. Open the **Workshop — Trace Explorer** dashboard, or run this TraceQL query:

```
{ duration > 2s }
```

You should see the slow traces listed. Compare with the fast ones.

### Step 3 — Identify the slow span

Open one slow trace. Expand the waterfall.

**Questions to answer:**
- Which service contains the critical path?
- Which span takes up most of the duration?
- How many child spans does that span have?

### Step 4 — Compare span attributes

Look at the `pricing.calculate` span attributes on a slow trace vs a fast one.

| Attribute | Slow trace | Fast trace |
|---|---|---|
| `pricing.strategy` | ? | ? |
| `pricing.route_candidate_count` | ? | ? |

What combination of request attributes (`transfer.source_currency`,
`transfer.target_currency`, `transfer.type`, `transfer.amount_bucket`)
is associated with the slow traces?

### Step 5 — Examine the FX spans

In the slow trace, look at the child spans of `pricing.calculate`.

- How many `fx.rate` spans are there?
- Do they overlap in time, or do they start one after another?
- What does the waterfall tell you about the execution order?

Draw the timeline you see:

```
Slow (actual):
  fx.rate [provider-a]  ───────────
  fx.rate [provider-b]              ───────────
  fx.rate [provider-c]                          ───────────

Fast (expected):
  fx.rate [provider-a]  ───────────
  fx.rate [provider-b]  ───────────
  fx.rate [provider-c]  ───────────
```

### Step 6 — Find the bug in the code

Open `app/src/main/kotlin/com/workshop/pricing/PricingController.kt`.

Look at the section that fetches FX rates for MULTI_ROUTE candidates.

**The bug:** The code uses `async { }.await()` inside a `map`. This *looks*
concurrent — it uses `async` — but each coroutine is immediately awaited
before the next one starts, forcing sequential execution.

```kotlin
// Buggy — sequential despite using async
candidates.map { candidate ->
    coroutineScope {
        async {
            fxClient.getRate(...)
        }.await()  // <-- awaited immediately inside the map
    }
}
```

### Step 7 — Fix the code

Replace the sequential loop with a truly concurrent implementation:

```kotlin
// Fixed — all coroutines launched before any is awaited
coroutineScope {
    candidates.map { candidate ->
        async {
            fxClient.getRate(
                request.sourceCurrency,
                request.targetCurrency,
                candidate
            )
        }
    }.awaitAll()
}
```

### Step 8 — Enable and run the regression test

In `app/src/test/kotlin/com/workshop/pricing/PricingConcurrencyTest.kt`:

1. Remove the `@Disabled` annotation.
2. Run: `./gradlew :app:test --tests "com.workshop.pricing.PricingConcurrencyTest"`
3. The test should now pass.

### Step 9 — Rebuild and verify

```bash
docker compose up -d --build pricing
./scripts/generate-slow-request.sh
```

Open Grafana and compare the new trace with the old one. The three FX spans
should now overlap. Total duration should drop from ~3 s to ~1 s.

---

## Investigation 2 — The hidden fan-out

### Background

Every transfer request calls the compliance service to screen the beneficiaries.
This is expected. But something is not right with how it is called.
There are no errors, no timeouts, and nothing unusual in the logs.

### Step 1 — Generate a request and open its trace

```bash
curl -s -X POST http://localhost:8080/transfers/prepare \
  -H "Content-Type: application/json" \
  -d '{"sourceCurrency":"EUR","targetCurrency":"USD","transferType":"BANK_TRANSFER","amount":1000}'
```

In Grafana, find this trace (use the **All transfer.prepare traces** panel or
search `{ name = "transfer.prepare" }`).

### Step 2 — Count the compliance spans

Expand the waterfall. Find the spans that call the compliance service.

**Questions to answer:**
- How many compliance spans do you see?
- Are they sequential or overlapping?
- How many beneficiaries does the customer have?
- Should the number of compliance calls equal the number of beneficiaries?

### Step 3 — Why logs miss this

Try to detect this problem using only logs:

- Each compliance call returns 200 OK in under 50 ms.
- The log line for each call looks completely normal.
- To detect the fan-out from logs you would need to: find all compliance
  calls for the same trace, group them, count them, and compare that count
  across many requests. This is impractical in production.

In the trace, you see it instantly: N identical sibling spans where there
should be 1.

### Step 4 — Find the bug in the code

Open `app/src/main/kotlin/com/workshop/transfer/TransferController.kt`.

Look at the compliance screening section.

**The bug:** Each beneficiary is screened with a separate HTTP call.

```kotlin
// Buggy — one HTTP call per beneficiary (N+1)
customer.beneficiaries.mapIndexed { index, beneficiary ->
    supportClient.screenCompliance(beneficiary.id, index)
}
```

A batch endpoint exists at `/compliance/screenBatch` but is not used.

### Step 5 — Fix the code

Replace the per-item loop with a single batch call:

```kotlin
// Fixed — one HTTP call for all beneficiaries
supportClient.screenComplianceBatch(customer.beneficiaries)
```

### Step 6 — Enable and run the regression test

In `app/src/test/kotlin/com/workshop/transfer/ComplianceFanoutTest.kt`:

1. Remove the `@Disabled` annotation.
2. Run: `./gradlew :app:test --tests "com.workshop.transfer.ComplianceFanoutTest"`
3. The test should now pass.

### Step 7 — Rebuild and verify

```bash
docker compose up -d --build transfer
```

Generate another request. In Grafana, compare the new trace with the old one.
There should now be exactly one compliance span instead of three.

---

## Reflection

| | Bug #1 (sequential coroutines) | Bug #2 (N+1 fan-out) |
|---|---|---|
| Visible in logs | No | No |
| Error or exception | No | No |
| Visible in traces | Yes — sequential waterfall | Yes — repeated sibling spans |
| Hard to see in code | Yes — `async` present, misleading | Yes — looks like normal iteration |
| Fix | `awaitAll()` | batch call |

Both bugs pass silently through every layer of observability except the trace.
