package com.workshop.transfer

import com.workshop.common.AuthValidateRequest
import com.workshop.common.AuthValidateResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class AuthClient(@Qualifier("authRestClient") private val restClient: RestClient) {

    fun validate(customerId: String, sessionToken: String): AuthValidateResponse =
        restClient.post().uri("/auth/validate")
            .body(AuthValidateRequest(customerId = customerId, sessionToken = sessionToken))
            .retrieve().body<AuthValidateResponse>()!!
}
