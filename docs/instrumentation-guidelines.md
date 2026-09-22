# Instrumentation Guidelines

## The annotation question: @NewSpan vs @WithSpan vs tracedSpan

This is the most common point of confusion in Spring + OTel codebases.
The short answer is in this decision table; the long answer follows.

### Decision table

| Scenario | Annotation / API | Why |
|---|---|---|
| HTTP request span (any controller method) | Nothing — enrich with `Span.current()` | The OTel agent auto-creates the SERVER span |
| Non-suspend method in a `@Service` / `@Component` | `@NewSpan` (Micrometer) | Spring AOP works correctly for blocking methods |
| `suspend fun` in any Spring bean | `tracedSpan { }` wrapper | AOP ends the span at first suspension; `@NewSpan` breaks |
| Kotlin non-Spring class | `@WithSpan` (OTel annotations) | No proxy, no double-span, not suspend-excluded |
| Method parameter → span attribute (non-suspend) | `@SpanTag` (Micrometer) or `@SpanAttribute` (OTel) | Auto-captures param as attribute; pairs with its annotation |

---

## Pattern 1 — Enrich the agent's span (controllers)

The OTel Java agent auto-creates an HTTP `SERVER` span for every request.
Do not create a new span in controllers — enrich the existing one:

```kotlin
@PostMapping("/prepare")
suspend fun prepare(@RequestBody request: PrepareTransferRequest): PrepareTransferResponse {
    // Attach business context to the span the agent already made.
    Span.current().apply {
        setAttribute("transfer.source_currency", request.sourceCurrency)
        setAttribute("transfer.target_currency", request.targetCurrency)
        setAttribute("transfer.amount_bucket", amountBucket(request.amount))
    }
    // ...
}
```

Zero overhead, zero extra spans, business attributes appear in the waterfall immediately.

---

## Pattern 2 — `@NewSpan` for non-suspend service methods (Micrometer)

`@NewSpan` (`io.micrometer.tracing.annotation.NewSpan`) is the enterprise-standard
annotation used in blocking Spring Boot applications. It is processed by Spring AOP
(`SpanAspect`) and works correctly for non-suspend methods.

### Setup required

Three things must be present beyond `spring-boot-starter-actuator`:

```kotlin
// build.gradle.kts
dependencies {
    // 1. The Micrometer → OTel bridge (version managed by Spring Boot BOM)
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    // 2. Spring AOP to power SpanAspect
    implementation("org.springframework.boot:spring-boot-starter-aop")
    // 3. (optional) For @Observed on classes
    //    The bean is declared in your config — see AgentOpenTelemetryConfig below
}
```

```kotlin
// AgentOpenTelemetryConfig.kt — exposes the agent's SDK as a Spring bean.
// Without this, Spring Boot creates a SECOND OTel SDK (its own SdkTracerProvider)
// because @ConditionalOnMissingBean(OpenTelemetry::class) checks the Spring
// context, not GlobalOpenTelemetry. Two SDKs = broken parent links + lost spans.
@Configuration
class AgentOpenTelemetryConfig {

    // Returns the SDK the Java agent registered at JVM startup.
    // @ConditionalOnMissingBean is now satisfied — Spring skips creating its own.
    @Bean
    fun openTelemetry(): OpenTelemetry = GlobalOpenTelemetry.get()

    // Required for @Observed to create spans at class/method level.
    @Bean
    fun observedAspect(registry: ObservationRegistry): ObservedAspect =
        ObservedAspect(registry)
}
```

### Usage

```kotlin
@Service
class TransferValidationService(private val complianceClient: ComplianceClient) {

    // Creates a "validate.transfer" child span automatically.
    // @SpanTag promotes the parameter as a span attribute.
    @NewSpan("validate.transfer")
    fun validate(
        @SpanTag("transfer.currency") currency: String,
        amount: Long
    ): ValidationResult {
        // ...
    }
}
```

### Why `@NewSpan` does NOT work on `suspend fun`

Spring AOP wraps the method call with `ProceedingJoinPoint.proceed()`.
For a `suspend fun`, `proceed()` returns `COROUTINE_SUSPENDED` at the first
suspension point — the coroutine hasn't completed yet. `SpanAspect` interprets
that as the method returning and immediately ends the span. The span duration
becomes the time to the first `delay()`/IO call, not the full operation.

This is a fundamental mismatch between Spring AOP's synchronous assumption
and Kotlin's cooperative multitasking model. There is no workaround short of
rewriting `SpanAspect` to understand coroutine continuations.

---

## Pattern 3 — `tracedSpan { }` for suspend functions

`tracedSpan` is a coroutine-safe wrapper around `spanBuilder` in `OtelHelper.kt`.
It is the correct replacement for `@NewSpan` in any `suspend fun`:

```kotlin
// PricingController.kt
return tracedSpan("pricing.calculate",
    attributes = {
        setAttribute("pricing.strategy", strategy.name)
        setAttribute("pricing.route_candidate_count", candidates.size.toLong())
    }
) { _ ->
    // work — span is active for the full coroutine lifetime,
    // including all suspension points and resumes
}
```

It handles start / end / error recording / exception re-throw in one call.
It works with `async { }` and `withContext { }` because it manages the scope
directly via `span.makeCurrent()` + `scope.close()` in a `finally` block that
runs when the coroutine completes, not when it first suspends.

### Explicit parent context for fan-out

When multiple coroutines run concurrently and each needs a child span under a
common parent, pass the parent context explicitly:

```kotlin
// pricingContext holds the pricing.calculate span as current
val rates = coroutineScope {
    candidates.map { candidate ->
        async {
            fxClient.getRate(candidate, pricingContext)  // explicit parent
        }
    }.awaitAll()
}

// FxClient.kt
suspend fun getRate(candidate: RouteCandidate, parentContext: Context): FxRateResponse =
    tracedSpan("fx.call", parent = parentContext, attributes = { ... }) {
        withContext(Context.current().asContextElement()) {
            webClient.post()...awaitBody()
        }
    }
```

Without the explicit parent, `Context.current()` varies per coroutine thread
and `fx.call` spans may attach to the wrong parent or appear as root spans.

---

## Pattern 4 — `@WithSpan` (OTel annotations, non-Spring classes)

`@WithSpan` (`io.opentelemetry.instrumentation.annotations.WithSpan`) is processed
by the OTel Java agent's ByteBuddy instrumentation at class-load time.

**Use it only on plain Kotlin classes that are not Spring-managed beans.**

On Spring-proxied beans (`@Service`, `@Component`, `@RestController`), the agent
instruments both the CGLIB proxy subclass and the concrete class, producing two
nested spans with the same name.

On `suspend fun`, the agent explicitly excludes the annotation (via
`KotlinCoroutineUtil.isKotlinSuspendMethod()` in `WithSpanInstrumentation`).
The annotation is silently ignored — no error, no span.

```kotlin
// Safe usage — not a Spring bean
class FxRateCalculator {
    @WithSpan("fx.rate.calculate")
    fun calculate(@SpanAttribute("currency.pair") pair: String): Double {
        // ...
    }
}
```

---

## Auto-instrumentation vs manual spans

The OTel Java agent auto-instruments:

| Framework / library | What it creates |
|---|---|
| Spring WebFlux (Netty) | HTTP `SERVER` span per request |
| WebClient | HTTP `CLIENT` span per outgoing call |
| Spring Data | DB `CLIENT` span per query |
| gRPC | `CLIENT`/`SERVER` spans |
| Kafka / RabbitMQ | `PRODUCER`/`CONSUMER` spans |

Manual spans are for **business operations** that the agent cannot know about:
`pricing.calculate`, `fx.call`, compliance screening, etc.

Rule of thumb: if it would appear in a sequence diagram for your domain,
it probably deserves a manual span with business attributes.

---

## Attribute naming

Use dot-separated namespaces that mirror your domain:

```
transfer.source_currency     transfer.amount_bucket
pricing.strategy             pricing.route_candidate_count
fx.source_currency           fx.provider
compliance.beneficiary_index
```

Follow [OpenTelemetry Semantic Conventions](https://opentelemetry.io/docs/concepts/semantic-conventions/)
for infrastructure attributes (`http.request.method`, `db.system`, etc.).

### What NOT to put in attributes

| Do not include | Reason |
|---|---|
| PII (names, account numbers, IPs) | Regulatory / GDPR |
| High-cardinality exact values at scale | Index size in Tempo / costs in cloud backends |
| Secrets or tokens | Security |
| Implementation details that reveal bugs | Defeats the workshop purpose |

### Bucketing numeric values

```kotlin
fun amountBucket(amount: Long): String =
    if (amount >= 10_000) "10000_PLUS" else "BELOW_10000"
```

Bucketing keeps cardinality bounded while still allowing useful filtering.

---

## Span-count as a diagnostic smell

If you spot N identical sibling spans in the waterfall where N equals a
collection size, you are looking at an N+1 problem:

```
compliance.screen   [10ms]   ─┐
compliance.screen   [10ms]    ├ 3 siblings = 3 HTTP calls = N+1
compliance.screen   [10ms]   ─┘
```

This is invisible in logs (3 healthy 200 OKs) but immediately obvious in
the trace. The fix: replace the loop with a single batch call.

---

## Context propagation checklist

- [ ] Every `suspend` WebClient call wrapped in `withContext(Context.current().asContextElement()) { }`
- [ ] Fan-out coroutines receive `parentContext: Context` explicitly
- [ ] `tracedSpan` used instead of `@NewSpan` for all `suspend fun`
- [ ] No `span.makeCurrent()` left open across a `delay()` or IO suspension
      (use `tracedSpan` which manages the scope in `finally`)
- [ ] `AgentOpenTelemetryConfig` present if `micrometer-tracing-bridge-otel`
      is on the classpath — otherwise two SDKs will be created
