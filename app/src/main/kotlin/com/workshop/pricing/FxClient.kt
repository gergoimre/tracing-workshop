package com.workshop.pricing

import com.workshop.common.FxRateRequest
import com.workshop.common.FxRateResponse
import com.workshop.common.RouteCandidate
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class FxClient(@Qualifier("fxRestClient") private val restClient: RestClient) {

    @WithSpan("fx.call")
    fun getRate(sourceCurrency: String, targetCurrency: String, candidate: RouteCandidate): FxRateResponse {
        Span.current().apply {
            setAttribute("fx.source_currency", sourceCurrency)
            setAttribute("fx.target_currency", targetCurrency)
            setAttribute("fx.provider", candidate.provider)
            setAttribute("route.type", candidate.routeType)
        }

        return restClient.post()
            .uri("/fx/rate")
            .body(FxRateRequest(sourceCurrency, targetCurrency, candidate.provider, candidate.routeType))
            .retrieve()
            .body<FxRateResponse>()!!
    }
}
