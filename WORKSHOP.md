# Workshop Exercises

This workshop has two independent investigations. Each teaches a different lesson
about why distributed tracing reveals problems that logs cannot.

---

## Setup

```bash
./start.sh
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

### Step 1 — Generate some traffic

```bash
./scripts/generate-traffic.sh
```

### Step 2 — Find slow traces in Grafana

1. Open http://localhost:3000 — the Workshop dashboard is the home page.
2. Click **"2 — Slow requests"** to open traces with duration > 2s, or use TraceQL:

```
{ resource.service.name = "transfer-service"
  && name = "http post /transfers/prepare"
  && duration > 2s }
```

### Step 3 — Identify the slow span

Open one slow trace. Expand the waterfall. Focus on the critical path.

- Which service contains the single longest span?
- How many child spans does that span have?
- Do those child spans overlap in time?

### Step 4 — Compare span attributes

Compare the slow trace against a fast one. Look at the span attributes — what is
different? Which attributes tell you what kind of request triggered the slowdown?

### Step 5 — Examine the structure

Look at the timing of the child spans under the slow parent span.

- Do they overlap in time, or do they start one after another?
- What does the waterfall shape tell you about how the work is being executed?

---

## Investigation 2 — The silent problem

### Background

The risk service occasionally takes significantly longer than usual. There are no
errors, no warnings, and nothing unusual in any log. Every individual call returns
200 OK in ~10ms.

### Step 1 — Generate traffic and find an affected trace

```bash
./scripts/generate-traffic.sh
```

Then open Grafana and explore traces from the risk service:

```
{ resource.service.name = "risk-service" }
```

Look for traces where the risk service span has an unusually high number of
child spans compared to a normal request.

### Step 2 — Examine the span structure

Expand the slow span inside the trace.

- How many child spans does it have compared to a normal request?
- What do the child spans represent?
- What does the waterfall shape reveal that the logs don't?

### Step 3 — Read the span attributes

Look at the attributes on the spans.

- What attributes can you find that explain what happened?
- Why is this pattern a problem even though every individual call is healthy?

### Step 4 — Why logs miss this

Open the logs for the risk service in Grafana → Loki:

```
{service_name="risk-service"}
```

- Every call returns 200 OK with normal latency.
- There are no errors, no warnings.
- Nothing in the logs indicates how many times an operation ran.

Click **"Logs for this span"** on any span to see only that span's log lines,
filtered by both `trace_id` and `span_id`.

---

## Reflection

After both investigations, discuss:

- What signals in the trace told you something was wrong?
- Could you have found either problem from logs alone? How long would it have taken?
- What attributes were most useful for narrowing down the cause?
- What would you instrument differently in your own services?
