package com.workshop.pricing

import com.workshop.common.PricingRequest
import com.workshop.common.PricingResponse
import io.micrometer.observation.annotation.Observed
import io.opentelemetry.api.trace.Span
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/pricing")
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "pricing")
class PricingController(
    private val fxClient: FxClient,
    private val routingClient: RoutingClient,
    private val routeCache: RouteCache,
    private val ledgerClient: LedgerClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/calculate")
    @Observed(name = "pricing.calculate")
    fun calculate(
        @RequestBody request: PricingRequest,
        @RequestHeader("X-Request-ID", defaultValue = "") requestId: String
    ): PricingResponse {
        val strategy = StrategyRules.resolve(
            request.sourceCurrency, request.targetCurrency,
            request.transferType, request.amount
        )

        val candidates = routeCache.get(
            request.sourceCurrency, request.targetCurrency,
            request.transferType, request.amount
        ) ?: routingClient.getCandidates(
            request.sourceCurrency, request.targetCurrency,
            request.transferType, request.amount
        ).candidates.also { fetched ->
            routeCache.put(
                request.sourceCurrency, request.targetCurrency,
                request.transferType, request.amount,
                fetched
            )
        }

        Span.current().apply {
            setAttribute("pricing.strategy", strategy.name)
            setAttribute("pricing.route_candidate_count", candidates.size.toLong())
            setAttribute("pricing.amount", request.amount)
        }

        log.info("Calculating pricing: strategy={} candidates={}", strategy.name, candidates.size)

        val rates = candidates.map { candidate ->
            fxClient.getRate(request.sourceCurrency, request.targetCurrency, candidate)
        }

        val best = rates.minByOrNull { it.rate }!!

        Span.current().setAttribute("pricing.best_rate", best.rate)

        val transferId = "txn-pending-${UUID.randomUUID().toString().take(8)}"
        ledgerClient.reserve(
            transferId = transferId,
            sourceCurrency = request.sourceCurrency,
            amount = 0L,
            provider = best.provider
        )

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
