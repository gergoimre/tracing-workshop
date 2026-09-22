package com.workshop.transfer

import com.workshop.common.PricingRequest
import com.workshop.common.PricingResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class PricingClient(@Qualifier("pricingRestClient") private val restClient: RestClient) {

    fun calculate(request: PricingRequest): PricingResponse =
        restClient.post().uri("/pricing/calculate")
            .body(request)
            .retrieve().body<PricingResponse>()!!
}
