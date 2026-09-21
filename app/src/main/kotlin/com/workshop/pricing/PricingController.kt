package com.workshop.pricing

import com.workshop.common.PricingRequest
import com.workshop.common.PricingResponse
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private val tracer = GlobalOpenTelemetry.getTracer("com.workshop.pricing", "0.0.1")

    @PostMapping("/calculate")
    suspend fun calculate(@RequestBody request: PricingRequest): PricingResponse {
        // The agent already created the SERVER span for POST /pricing/calculate.
        // We capture it as the parent so pricing.calculate sits directly under it.
        val agentServerSpan = Span.current()
        val serverContext = Context.current()

        val strategy = StrategyRules.resolve(
            request.sourceCurrency,
            request.targetCurrency,
            request.transferType,
            request.amountBucket
        )

        val candidates = routingClient.getCandidates(
            request.sourceCurrency,
            request.targetCurrency,
            request.transferType,
            request.amountBucket
        ).candidates

        // Create pricing.calculate as an INTERNAL child of the agent's server span.
        val pricingSpan = tracer.spanBuilder("pricing.calculate")
            .setSpanKind(SpanKind.INTERNAL)
            .setParent(serverContext)
            .startSpan()
        pricingSpan.setAttribute("pricing.strategy", strategy.name)
        pricingSpan.setAttribute("pricing.route_candidate_count", candidates.size.toLong())

        // Build a context with pricingSpan as current so fx.call spans nest under it.
        val pricingContext = serverContext.with(pricingSpan)

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
