package com.workshop.transfer

import com.workshop.common.RiskScoreRequest
import com.workshop.common.RiskScoreResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class RiskClient(@Qualifier("riskRestClient") private val restClient: RestClient) {

    fun score(customerId: String, targetCurrency: String, amountBucket: String, memo: String?): RiskScoreResponse =
        restClient.post().uri("/risk/score")
            .body(RiskScoreRequest(
                customerId = customerId,
                targetCurrency = targetCurrency,
                amountBucket = amountBucket,
                memo = memo
            ))
            .retrieve().body<RiskScoreResponse>()!!
}
