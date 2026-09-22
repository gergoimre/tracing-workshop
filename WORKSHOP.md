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

Open one slow trace. Expand the waterfall.

- Which service contains the critical path?
- Which span takes up most of the duration?
- How many child spans does that span have?

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

## Investigation 2 — The cache that never hits

### Background

The pricing service has a route-candidate cache to avoid redundant calls to the
support service. Performance metrics show the cache hit rate is 0%. Every single
request fetches routing candidates from scratch. Nobody can explain why — the
cache code looks correct and there are no errors anywhere.

### Step 1 — Generate several identical requests

```bash
for i in 1 2 3 4 5; do
  curl -s -o /dev/null -X POST http://localhost:8080/transfers/prepare \
    -H "Content-Type: application/json" \
    -d '{"sourceCurrency":"EUR","targetCurrency":"USD","transferType":"BANK_TRANSFER","amount":500,"recipientId":"rec-1"}'
done
```

All five requests use identical routing parameters. With a working cache, only
the first should hit the support service for routing candidates.

### Step 2 — Find the traces in Grafana

Open several `POST /transfers/prepare` traces for the EUR→USD requests.

Expand the waterfall inside `pricing.calculate` for each one.

- Is `GET /routing/candidates` present in every single trace?
- Does it ever disappear (cache hit = no downstream call)?

### Step 3 — Why logs miss this

- Every routing call returns 200 OK in a few milliseconds.
- The cache logs "cache miss" — but cache misses are normal on first use, so this line is never alarming.
- To detect a 0% hit rate from logs you would need to aggregate miss/hit counters across thousands of requests. In a trace, zero hits is visible immediately: every single trace has a `GET /routing/candidates` span.

### Step 4 — Find the bug in the code

Open `app/src/main/kotlin/com/workshop/pricing/RouteCache.kt`.

Look at the `CacheKey` data class.

**The bug:** The key includes `requestId`, which is a fresh UUID generated by the
transfer service for each request. Every key is globally unique, so no two requests
ever share a cache entry.

```kotlin
private data class CacheKey(
    val sourceCurrency: String,
    val targetCurrency: String,
    val transferType: String,
    val amountBucket: String,
    val requestId: String   // <-- always unique, defeats the cache entirely
)
```

The routing result depends only on the four currency/type/bucket fields —
not on which specific request triggered the lookup.

### Step 5 — Fix the code

Remove `requestId` from `CacheKey`:

```kotlin
private data class CacheKey(
    val sourceCurrency: String,
    val targetCurrency: String,
    val transferType: String,
    val amountBucket: String
)
```

Also remove the `requestId` parameters from `get()` and `put()`.

### Step 6 — Enable and run the regression test

In `app/src/test/kotlin/com/workshop/pricing/RouteCacheTest.kt`:

1. Remove the `@Disabled` annotation.
2. Run: `./gradlew :app:test --tests "com.workshop.pricing.RouteCacheTest"`
3. The test should now pass.

### Step 7 — Rebuild and verify

```bash
./gradlew :app:bootJar -q
docker compose up -d --build pricing
```

Fire the same 5 identical requests again. Only the first trace should have a
`GET /routing/candidates` span. The other four should go straight to `fx.call`.

---

## Reflection

| | Bug #1 (sequential FX) | Bug #2 (broken cache key) |
|---|---|---|
| Visible in logs | No | No (cache misses look normal) |
| Error or exception | No | No |
| Visible in traces | Yes — sequential waterfall | Yes — routing call on every trace |
| Hard to see in code | Yes — sequential map looks fine | Yes — requestId in key looks defensive |
| Fix | `parallelStream()` | Remove `requestId` from `CacheKey` |

Both bugs are invisible to every observability signal except the trace structure.
