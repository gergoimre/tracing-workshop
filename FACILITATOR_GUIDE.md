# Facilitator Guide

This guide contains answer keys, timing recommendations, and talking points
for running the tracing workshop. Do not share this file with participants.

---

## Suggested timing

| Section | Duration |
|---|---|
| Setup + verify environment | 10 min |
| Tracing primer (docs/tracing-primer.md) | 15 min |
| Investigation 1 — slow transfer | 30–40 min |
| Debrief Investigation 1 | 10 min |
| Investigation 2 — compliance fan-out | 20–30 min |
| Debrief Investigation 2 | 10 min |
| Trade-offs discussion | 15 min |
| Total | ~2 h |

---

## Investigation 1 — Answer key

**Which request triggers the bug?**
`sourceCurrency=EUR, targetCurrency=BRL, transferType=BANK_TRANSFER, amount≥10000`

All four conditions must be true. This is intentional — participants who assume
"all BRL requests are slow" or "all large-amount requests are slow" will be
wrong. Combining trace attributes is the only way to find it.

**Why is it slow?**
The pricing service uses `StrategyRules` to select `MULTI_ROUTE` for that
combination. MULTI_ROUTE fetches FX rates for three route candidates.
The coroutine loop uses `async { }.await()` inside `map`, which awaits each
coroutine immediately — forcing sequential execution despite using `async`.

Three 1-second FX calls run one after another → ~3 s total.
After the fix they run concurrently → ~1 s total.

**What the trace shows:**
- `pricing.calculate` span takes ~3 s
- Three `fx.rate` child spans appear end-to-end (no overlap) in the waterfall
- Each has `fx.provider`, `route.type`, `fx.source_currency`, `fx.target_currency`
- No attribute says "sequential" — participants must read the timing

**The fix:**
```kotlin
// In PricingController.kt, replace the map block with:
coroutineScope {
    candidates.map { candidate ->
        async {
            fxClient.getRate(request.sourceCurrency, request.targetCurrency, candidate)
        }
    }.awaitAll()
}
```

**Common wrong turns:**
- "The FX service is slow" — yes, ~1 s per call is by design. That's not the bug.
- "We should cache FX rates" — valid optimisation, but not the root cause here.
- Editing the FX service delay — reinforforce: the latency is expected,
  the orchestration is wrong.

**Why logs can't find this:**
Each FX call returns 200 in exactly ~1 s. Every log line looks healthy.
You would need to correlate the start times of three calls across a service
boundary to notice they are sequential. In production log volumes, this is
effectively impossible without traces.

---

## Investigation 2 — Answer key

**What is the bug?**
`TransferController` calls `supportClient.screenCompliance(beneficiary.id, index)`
once per beneficiary in a `mapIndexed` loop. The customer has 3 beneficiaries,
so every request makes 3 compliance calls instead of 1.

**What the trace shows:**
Three identical `compliance.screen` sibling spans under `transfer.prepare`.
They may run sequentially (they are `await`ed in order) but the key tell is
the *count* — 3 where there should be 1.

**The fix:**
```kotlin
// In TransferController.kt, replace the mapIndexed block with:
supportClient.screenComplianceBatch(customer.beneficiaries)
```

**Why logs can't find this:**
- Each call is a healthy 200 in ~5–20 ms. Nothing is wrong per call.
- There is no error, no warning, no timeout.
- To detect this from logs you would need to grep all compliance calls,
  group by trace/request ID, count per request, and flag requests with N > 1.
  Even then, you need the trace ID to correlate — which means you already
  need trace context in your logs.

**Talking point — "logs with trace IDs":**
If participants suggest "just add trace IDs to logs and correlate", agree that
this is valuable — but point out: (a) you still need to write the grouping/
counting query, (b) cardinality explodes in high-volume services, and (c) you
still end up rebuilding a trace manually. The trace gives you the answer
visually in under 10 seconds.

---

## Attribute audit — what participants should NOT find

The following attributes are deliberately absent from all spans:
- Nothing encodes "sequential" vs "concurrent"
- Nothing encodes "N calls" vs "1 batch call"
- No attribute names the bug directly

Participants must use timing (waterfall) and span count to find the bugs.
This is the point: traces reveal structure and timing; attributes describe
business context.

---

## Facilitation tips

- Resist giving hints before participants have spent at least 10 minutes exploring.
- If a group is stuck on Investigation 1, ask: "How many FX spans do you see,
  and do they start at the same time?"
- If a group is stuck on Investigation 2, ask: "How many beneficiaries does
  the customer have? How many compliance spans do you see?"
- The best learning moment is when a participant says "I would never have
  found this in the logs." Pause and let that land.
- The `solution.patch` file contains both fixes. Apply it with:
  `git apply solution.patch`
