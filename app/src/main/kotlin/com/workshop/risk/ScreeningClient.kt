package com.workshop.risk

import com.workshop.common.ScreeningCheckRequest
import com.workshop.common.ScreeningCheckResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "risk")
class ScreeningClient(@Qualifier("screeningRestClient") private val restClient: RestClient) {

    fun check(term: String, customerId: String): ScreeningCheckResponse =
        restClient.post().uri("/screening/check")
            .body(ScreeningCheckRequest(term = term, customerId = customerId))
            .retrieve().body<ScreeningCheckResponse>()!!
}
