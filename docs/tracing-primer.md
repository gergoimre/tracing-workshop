# Distributed Tracing Primer

## What is a trace?

A **trace** represents a single request as it travels through a distributed
system. It is made up of **spans** — units of work with a start time, end time,
and a set of key-value attributes.

```
Trace (one request)
│
└── transfer.prepare  [0ms ─────────────────── 3100ms]
    ├── customer.lookup  [5ms ── 15ms]
    ├── limits.check     [16ms ── 24ms]
    ├── compliance.screen [25ms ── 35ms]
    ├── compliance.screen [35ms ── 45ms]
    ├── compliance.screen [45ms ── 55ms]
    └── pricing.calculate [60ms ─────────────── 3100ms]
        ├── fx.rate  [65ms ─── 1065ms]
        ├── fx.rate             [1066ms ── 2066ms]
        └── fx.rate                         [2067ms ── 3067ms]
```

## Spans

Each span has:
- A **name** (e.g. `pricing.calculate`)
- A **start time** and **end time**
- **Attributes** — structured key-value metadata (e.g. `transfer.source_currency = EUR`)
- A **parent span ID** (except the root span)
- A **status** (OK or ERROR)

## Context propagation

When service A calls service B, it injects the current **trace context** into
the outgoing request (via HTTP headers, e.g. `traceparent`). Service B extracts
it and creates a child span. This is how a single trace spans multiple services.

The OTel Java agent handles this automatically for HTTP calls.

## The waterfall view

Grafana Tempo displays traces as a waterfall: each span is a horizontal bar,
positioned in time. Spans that overlap are concurrent. Spans that are
end-to-end are sequential.

This visual makes concurrency bugs and N+1 patterns immediately obvious —
no querying required.

## Why traces beat logs for some problems

| Problem | Logs | Traces |
|---|---|---|
| An error occurred | Easy — log the error | Easy — span has ERROR status |
| A service is slow | Hard — requires correlating timestamps | Easy — long span, visible in waterfall |
| Sequential vs concurrent calls | Very hard — requires correlating start times across services | Easy — visible as overlap in waterfall |
| N+1 calls to a service | Hard — requires counting log lines per request | Easy — N identical sibling spans |
| Which request combination is slow | Hard — requires aggregating many log fields | Easy — filter by span attributes |

## TraceQL

Tempo supports TraceQL for querying traces:

```
# All traces over 2 seconds
{ duration > 2s }

# Traces where pricing.strategy is MULTI_ROUTE
{ span.pricing.strategy = "MULTI_ROUTE" }

# Traces with more than 2 compliance spans
{ name = "compliance.screen" } | count() > 2

# Slow transfer.prepare traces with specific attributes
{ name = "transfer.prepare" && span.transfer.target_currency = "BRL" && duration > 2s }
```
