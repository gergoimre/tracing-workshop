# Trade-offs: Logs, Metrics, Traces

Distributed tracing is not a replacement for logs or metrics. Each signal
has strengths and weaknesses. The right answer is usually all three.

## Logs

**Strengths:**
- Cheap to produce and store at low volume
- Easy to add to existing code
- Good for unstructured events and detailed error context
- Every developer already understands them

**Weaknesses:**
- No inherent structure across service boundaries
- Correlating related log lines requires trace IDs — which means you need
  traces anyway
- High-volume correlation (e.g. "count compliance calls per request") is
  expensive and requires pre-built queries
- Timing relationships between calls are not directly visible

**When logs are enough:**
A single-service application where all relevant context fits on one log line.

## Metrics

**Strengths:**
- Extremely efficient to store (aggregated, not per-request)
- Great for alerting on rates, counts, and percentiles
- Native support in Grafana, Prometheus, Datadog, etc.

**Weaknesses:**
- No request-level detail after aggregation
- Cannot answer "which specific request was slow"
- Cannot show the relationship between spans within a request

**When metrics are enough:**
You need to know that p99 latency crossed a threshold, but you don't need
to know which request caused it.

## Traces

**Strengths:**
- Request-level, end-to-end visibility across service boundaries
- Timing relationships (sequential vs concurrent) are visually obvious
- N+1 patterns appear as repeated sibling spans
- Attributes allow filtering to specific request combinations
- Context propagation is automatic with the OTel agent

**Weaknesses:**
- Storage cost scales with request volume (mitigated by sampling)
- Requires instrumentation (auto-instrumentation helps significantly)
- High-cardinality attributes increase index size in backends
- Not a replacement for logging — missing detailed error context
- Sampling means some requests are not recorded

**When traces are essential:**
- Performance problems that are invisible in logs (sequential coroutines, N+1)
- Multi-service request flows
- Debugging which *specific* request combination is slow
- Comparing before/after behavior after a change

## Sampling

Recording every trace is expensive at scale. Common strategies:

| Strategy | Description | Use case |
|---|---|---|
| Always-on | Record 100% of traces | Development, this workshop |
| Head-based (rate) | Record N% of requests at ingestion | High-volume production |
| Tail-based | Record only traces matching rules (e.g. errors, slow) | Production, targeted debugging |

This workshop uses `always_on` sampling so all traces are visible immediately.
In production, consider tail-based sampling to capture errors and slow requests
while discarding healthy fast traces.

## Cardinality cost

Every unique attribute value combination creates a new index entry in the
trace backend. This is much cheaper than metrics cardinality (which creates
new time series), but still relevant at scale.

Guidelines:
- Bucket numeric values
- Avoid request/trace IDs as span attributes
- Use enums for categorical data
- Monitor backend index size as traffic grows

## The combined picture

```
Alert fires (metric):   p99 latency > 3s on transfer-service

Find the request (trace): { span.name = "transfer.prepare" && duration > 2s }

Understand the structure (trace waterfall):
  pricing.calculate  ─────────────────────────── 3.0s
    fx.rate          ────────
    fx.rate                   ────────
    fx.rate                            ────────

Confirm the fix (log + trace):
  Log: "Applied MULTI_ROUTE strategy, candidates=3"
  Trace after fix: all three fx.rate spans overlap
```

No single signal tells the complete story. The metric alerts you. The trace
shows you the structure. The log gives you the full context of the decision.
