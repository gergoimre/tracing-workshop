package com.workshop.transfer

import com.workshop.common.PricingRequest
import com.workshop.common.PricingResponse
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.context.Context
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class PricingClient(@Qualifier("pricingWebClient") private val webClient: WebClient) {

    suspend fun calculate(request: PricingRequest): PricingResponse =
        withContext(Context.current().asContextElement()) {
            webClient.post()
                .uri("/pricing/calculate")
                .bodyValue(request)
                .retrieve()
                .awaitBody()
        }
}
