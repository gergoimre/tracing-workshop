package com.workshop.pricing

import com.workshop.common.PricingRequest
import com.workshop.common.PricingResponse
import io.opentelemetry.api.GlobalOpenTelemetry
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
        val span = tracer.spanBuilder("pricing.calculate")
            .setSpanKind(SpanKind.SERVER)
            .setParent(Context.current())
            .startSpan()
        val scope = span.makeCurrent()
        return try {
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

            span.setAttribute("pricing.strategy", strategy.name)
            span.setAttribute("pricing.route_candidate_count", candidates.size.toLong())

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
                            candidate
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
            scope.close()
            span.end()
        }
    }
}

private fun <T, R : Comparable<R>> List<T>.minByKey(selector: (T) -> R): T =
    minByOrNull(selector) ?: throw NoSuchElementException("Empty list")
