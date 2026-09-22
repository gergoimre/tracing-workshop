package com.workshop.pricing

import com.workshop.common.PricingRequest
import com.workshop.common.PricingResponse
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/pricing")
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "pricing")
class PricingController(
    private val fxClient: FxClient,
    private val routingClient: RoutingClient
) {
    // Note: @WithSpan is not used here. On Spring-proxied beans the agent's
    // bytecode instrumentation fires twice (once on the proxy, once on the
    // concrete method), producing duplicate nested spans. Manual span creation
    // with tracer.spanBuilder() is explicit and collision-free.
    private val tracer = GlobalOpenTelemetry.getTracer("com.workshop.pricing", "0.0.1")

    @PostMapping("/calculate")
    suspend fun calculate(@RequestBody request: PricingRequest): PricingResponse {
        val strategy = StrategyRules.resolve(
            request.sourceCurrency,
            request.targetCurrency,
            request.transferType,
            request.amountBucket
        )

        val candidates = withContext(Context.current().asContextElement()) {
            routingClient.getCandidates(
                request.sourceCurrency,
                request.targetCurrency,
                request.transferType,
                request.amountBucket
            ).candidates
        }

        // Manually create pricing.calculate as a child of the agent's server
        // span — clean, single span, no proxy duplication.
        val pricingSpan = tracer.spanBuilder("pricing.calculate")
            .setSpanKind(SpanKind.INTERNAL)
            .setParent(Context.current())
            .startSpan()
        pricingSpan.setAttribute("pricing.strategy", strategy.name)
        pricingSpan.setAttribute("pricing.route_candidate_count", candidates.size.toLong())

        val pricingContext = Context.current().with(pricingSpan)

        return try {
            // ──────────────────────────────────────────────────────────────────
            // BUG #1: The async { }.await() pattern inside map looks concurrent
            // but forces sequential execution — each coroutine is started and
            // immediately awaited before the next one is launched.
            //
            // The fix (in solution.patch) is:
            //   coroutineScope { candidates.map { async { fxClient.getRate(...) } }.awaitAll() }
            // ──────────────────────────────────────────────────────────────────
            val rates = candidates.map { candidate ->
                coroutineScope {
                    async {
                        fxClient.getRate(
                            request.sourceCurrency,
                            request.targetCurrency,
                            candidate,
                            pricingContext
                        )
                    }.await()  // <-- awaited immediately: sequential, not concurrent
                }
            }

            val best = rates.minByKey { it.rate }

            PricingResponse(
                strategy = strategy.name,
                routeCandidateCount = candidates.size,
                bestRate = best.rate,
                provider = best.provider,
                routeType = best.routeType
            )
        } finally {
            pricingSpan.end()
        }
    }
}

private fun <T, R : Comparable<R>> List<T>.minByKey(selector: (T) -> R): T =
    minByOrNull(selector) ?: throw NoSuchElementException("Empty list")
