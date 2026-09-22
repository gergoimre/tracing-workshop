package com.workshop.pricing

import com.workshop.common.PricingRequest
import com.workshop.common.PricingResponse
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/calculate")
    fun calculate(@RequestBody request: PricingRequest): PricingResponse =
        calculatePricing(request)

    // @WithSpan on a separate non-controller method avoids the agent's
    // SERVER span (which the agent already created for POST /pricing/calculate).
    // This creates "pricing.calculate" as a clean INTERNAL child span.
    @WithSpan("pricing.calculate")
    fun calculatePricing(request: PricingRequest): PricingResponse {
        val strategy = StrategyRules.resolve(
            request.sourceCurrency, request.targetCurrency,
            request.transferType, request.amountBucket
        )
        val candidates = routingClient.getCandidates(
            request.sourceCurrency, request.targetCurrency,
            request.transferType, request.amountBucket
        ).candidates

        // Enrich the span @WithSpan created.
        Span.current().apply {
            setAttribute("pricing.strategy", strategy.name)
            setAttribute("pricing.route_candidate_count", candidates.size.toLong())
        }

        log.info("Calculating pricing: strategy={} candidates={}", strategy.name, candidates.size)

        // ──────────────────────────────────────────────────────────────────
        // BUG #1: Each FX call is made sequentially in a plain loop.
        // Each call takes ~1s, so 3 candidates = ~3s total.
        //
        // The bug is subtle because the code looks like ordinary iteration —
        // there is no obvious indication that these calls could run in parallel.
        // In a trace, the three fx.call spans appear end-to-end in the waterfall.
        //
        // The fix (in solution.patch) replaces this with parallel execution:
        //   val rates = candidates.parallelStream()
        //       .map { fxClient.getRate(request.sourceCurrency, request.targetCurrency, it) }
        //       .toList()
        // ──────────────────────────────────────────────────────────────────
        val rates = candidates.map { candidate ->
            fxClient.getRate(request.sourceCurrency, request.targetCurrency, candidate)
        }

        val best = rates.minByOrNull { it.rate }!!

        log.info("Pricing complete: bestRate={} provider={} strategy={}", best.rate, best.provider, strategy.name)

        return PricingResponse(
            strategy = strategy.name,
            routeCandidateCount = candidates.size,
            bestRate = best.rate,
            provider = best.provider,
            routeType = best.routeType
        )
    }
}
