# Instrumentation Guidelines

## Auto-instrumentation vs manual spans

This workshop uses the **OTel Java agent** for auto-instrumentation. It
instruments Spring WebFlux, WebClient, and HTTP automatically — you get
service-to-service context propagation for free.

Manual spans are added for business-level operations that the agent does not
know about:

```kotlin
// Manual span with business attributes
val span = tracer.spanBuilder("pricing.calculate")
    .setSpanKind(SpanKind.SERVER)
    .setParent(Context.current())
    .startSpan()
span.setAttribute("pricing.strategy", strategy.name)
span.setAttribute("pricing.route_candidate_count", candidates.size.toLong())
```

**Rule of thumb:** use auto-instrumentation for infrastructure (HTTP, DB, queues)
and manual spans for business operations and decisions.

## Attribute naming

Use dot-separated namespaces that reflect your domain:

```
transfer.source_currency
transfer.target_currency
transfer.type
transfer.amount_bucket
pricing.strategy
pricing.route_candidate_count
fx.source_currency
fx.provider
compliance.beneficiary_index
```

Avoid generic names like `type`, `id`, `value` — they collide across services.

Follow the [OpenTelemetry Semantic Conventions](https://opentelemetry.io/docs/concepts/semantic-conventions/)
for standard infrastructure attributes (HTTP, DB, messaging).

## What to put in attributes — and what not to

**Do include:**
- Business identifiers (customer tier, currency pair, transfer type)
- Bucketed numeric values (amount range, not exact amount)
- Decision outcomes (pricing strategy, compliance result)
- Counts of operations (route_candidate_count)

**Do not include:**
- PII (names, account numbers, IP addresses)
- High-cardinality exact values (transfer IDs, exact amounts at scale)
- Secrets or tokens
- Implementation details that leak the bug (e.g. `is_sequential = true`)

## Span-count as an instrumentation smell

If you find yourself adding a `call_count` attribute to a span, consider
whether the high count is itself a bug. In this workshop, the compliance
fan-out is detectable from the trace structure — no attribute is needed.

**Pattern to watch for:** N identical sibling spans where N should be 1.
This is the trace signature of an N+1 problem.

## Cardinality

Each unique combination of attribute values creates a new time series in
metrics-based backends. Keep attribute cardinality low:

- Bucket numeric values: `10000_PLUS` not `15000`
- Use enums for categorical values: `BANK_TRANSFER` not free text
- Never use request/trace IDs as attribute values in spans you aggregate

## Context propagation

The OTel agent propagates the `traceparent` header automatically via WebClient.
If you use a non-instrumented transport (e.g. raw `HttpURLConnection`, a custom
gRPC client), you must inject/extract context manually using the OTel propagator API.

## Coroutines and context

The OTel Java agent patches the Kotlin coroutine dispatcher to carry the current
`Context` across suspension points. Manual spans created with `span.makeCurrent()`
are correctly scoped to their coroutine. However, when using `async { }`, each
coroutine inherits the context at the point it was *launched*, not when it is
resumed. This is important for understanding how the context flows through the
concurrent FX calls after the fix.
