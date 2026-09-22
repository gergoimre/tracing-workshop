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

        Span.current().apply {
            setAttribute("pricing.strategy", strategy.name)
            setAttribute("pricing.route_candidate_count", candidates.size.toLong())
        }

        log.info("Calculating pricing: strategy={} candidates={}", strategy.name, candidates.size)

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
