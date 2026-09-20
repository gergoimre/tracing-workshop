package com.workshop.pricing

import com.workshop.common.FxRateRequest
import com.workshop.common.FxRateResponse
import com.workshop.common.RouteCandidate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class FxClient(@Qualifier("fxWebClient") private val webClient: WebClient) {

    suspend fun getRate(
        sourceCurrency: String,
        targetCurrency: String,
        candidate: RouteCandidate
    ): FxRateResponse =
        webClient.post()
            .uri("/fx/rate")
            .bodyValue(
                FxRateRequest(
                    sourceCurrency = sourceCurrency,
                    targetCurrency = targetCurrency,
                    provider = candidate.provider,
                    routeType = candidate.routeType
                )
            )
            .retrieve()
            .awaitBody()
}
