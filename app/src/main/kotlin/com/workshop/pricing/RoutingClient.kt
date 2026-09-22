package com.workshop.pricing

import com.workshop.common.RoutingCandidatesResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class RoutingClient(@Qualifier("supportRestClient") private val restClient: RestClient) {

    fun getCandidates(
        sourceCurrency: String,
        targetCurrency: String,
        transferType: String,
        amountBucket: String
    ): RoutingCandidatesResponse =
        restClient.get()
            .uri { builder ->
                builder.path("/routing/candidates")
                    .queryParam("sourceCurrency", sourceCurrency)
                    .queryParam("targetCurrency", targetCurrency)
                    .queryParam("transferType", transferType)
                    .queryParam("amountBucket", amountBucket)
                    .build()
            }
            .retrieve()
            .body<RoutingCandidatesResponse>()!!
}
