package com.workshop.auth

import com.workshop.common.SessionResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "auth")
class SessionClient(@Qualifier("sessionRestClient") private val restClient: RestClient) {

    fun getSession(token: String): SessionResponse =
        restClient.get().uri("/sessions/$token").retrieve().body<SessionResponse>()!!
}
