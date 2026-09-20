package com.workshop.transfer

import com.workshop.common.PricingRequest
import com.workshop.common.PricingResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class PricingClient(@Qualifier("pricingWebClient") private val webClient: WebClient) {

    suspend fun calculate(request: PricingRequest): PricingResponse =
        webClient.post()
            .uri("/pricing/calculate")
            .bodyValue(request)
            .retrieve()
            .awaitBody()
}
