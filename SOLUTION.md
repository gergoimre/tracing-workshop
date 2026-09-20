# Solution

This file describes both bugs, their root causes, and the minimal fixes.
Apply the patch with: `git apply solution.patch`

---

## Bug #1 — Sequential coroutines in the pricing service

**File:** `app/src/main/kotlin/com/workshop/pricing/PricingController.kt`

### Root cause

The MULTI_ROUTE path fetches FX rates for three route candidates using this loop:

```kotlin
// Buggy
val rates = candidates.map { candidate ->
    coroutineScope {
        async {
            fxClient.getRate(request.sourceCurrency, request.targetCurrency, candidate)
        }.await()  // <-- awaited immediately before the next iteration
    }
}
```

`async { }.await()` *inside* a `map` is not concurrent. Each coroutine is
started and immediately suspended until it completes before the next iteration
of `map` begins. The result is exactly the same as calling the suspend function
directly with no `async` at all.

Because the FX service takes ~1 s per call, three sequential calls take ~3 s.

### Fix

Launch all coroutines first, then await all results:

```kotlin
// Fixed
val rates = coroutineScope {
    candidates.map { candidate ->
        async {
            fxClient.getRate(request.sourceCurrency, request.targetCurrency, candidate)
        }
    }.awaitAll()
}
```

`awaitAll()` suspends until every `Deferred` completes. All three FX calls are
in-flight simultaneously. Total duration: ~1 s regardless of candidate count.

### Why the trace reveals it

The three `fx.rate` spans appear end-to-end in the waterfall (no overlap).
No attribute says "sequential" — the timing is the only evidence. This is
deliberate: attributes describe business context, not implementation details.

### Regression test

`app/src/test/kotlin/com/workshop/pricing/PricingConcurrencyTest.kt`

Remove `@Disabled` and run:
```bash
./gradlew :app:test --tests "com.workshop.pricing.PricingConcurrencyTest"
```

---

## Bug #2 — N+1 compliance fan-out in the transfer service

**File:** `app/src/main/kotlin/com/workshop/transfer/TransferController.kt`

### Root cause

Every transfer screens the customer's beneficiaries for compliance.
The buggy implementation calls the single-item endpoint once per beneficiary:

```kotlin
// Buggy — N HTTP calls
customer.beneficiaries.mapIndexed { index, beneficiary ->
    supportClient.screenCompliance(beneficiary.id, index)
}
```

The support service exposes a batch endpoint (`POST /compliance/screenBatch`)
that accepts a list and returns all results in one call, but it is never used.

With 3 beneficiaries: 3 HTTP round-trips to the support service on every single
transfer request. In production at scale this would saturate the compliance
service with redundant calls.

### Fix

```kotlin
// Fixed — 1 HTTP call
supportClient.screenComplianceBatch(customer.beneficiaries)
```

### Why logs miss it

Each individual call returns 200 OK in a few milliseconds. There is no error,
no warning, no slow query. From logs, every call looks correct. To detect
this pattern from logs you would need to group calls by request, count them,
and compare across requests — impractical in production without pre-built tooling.

In the trace: three identical `compliance.screen` sibling spans are visible
immediately. The fix replaces them with one `compliance.screenBatch` span.

### Regression test

`app/src/test/kotlin/com/workshop/transfer/ComplianceFanoutTest.kt`

Remove `@Disabled` and run:
```bash
./gradlew :app:test --tests "com.workshop.transfer.ComplianceFanoutTest"
```
