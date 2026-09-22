package com.workshop.fx

import com.workshop.common.FxRateRequest
import com.workshop.common.FxRateResponse
import io.opentelemetry.api.trace.Span
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/fx")
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "fx")
class FxController(
    @Value("\${fx.delay-ms:1000}") private val delayMs: Long
) {

    @PostMapping("/rate")
    fun getRate(@RequestBody request: FxRateRequest): FxRateResponse {
        Span.current().apply {
            setAttribute("fx.source_currency", request.sourceCurrency)
            setAttribute("fx.target_currency", request.targetCurrency)
            setAttribute("fx.provider", request.provider)
            setAttribute("route.type", request.routeType)
        }

        Thread.sleep(delayMs)

        return FxRateResponse(
            sourceCurrency = request.sourceCurrency,
            targetCurrency = request.targetCurrency,
            rate = simulatedRate(request.sourceCurrency, request.targetCurrency),
            provider = request.provider,
            routeType = request.routeType
        )
    }

    private fun simulatedRate(source: String, target: String): Double = when ("$source/$target") {
        "EUR/BRL" -> 5.42
        "EUR/USD" -> 1.08
        "GBP/BRL" -> 6.31
        "GBP/USD" -> 1.27
        else -> 1.0
    }
}
