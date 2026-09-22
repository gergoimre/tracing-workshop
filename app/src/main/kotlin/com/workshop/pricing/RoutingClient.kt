package com.workshop.pricing

import com.workshop.common.RoutingCandidatesResponse
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.context.Context
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class RoutingClient(@Qualifier("supportWebClient") private val webClient: WebClient) {

    suspend fun getCandidates(
        sourceCurrency: String,
        targetCurrency: String,
        transferType: String,
        amountBucket: String
    ): RoutingCandidatesResponse =
        withContext(Context.current().asContextElement()) {
            webClient.get()
                .uri { builder ->
                    builder.path("/routing/candidates")
                        .queryParam("sourceCurrency", sourceCurrency)
                        .queryParam("targetCurrency", targetCurrency)
                        .queryParam("transferType", transferType)
                        .queryParam("amountBucket", amountBucket)
                        .build()
                }
                .retrieve()
                .awaitBody()
        }
}
