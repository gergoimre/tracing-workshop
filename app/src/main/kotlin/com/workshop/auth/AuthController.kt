package com.workshop.auth

import com.workshop.common.AuthValidateRequest
import com.workshop.common.AuthValidateResponse
import io.opentelemetry.api.trace.Span
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "auth")
class AuthController(private val sessionClient: SessionClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/validate")
    fun validate(@RequestBody request: AuthValidateRequest): AuthValidateResponse {
        val session = sessionClient.getSession(request.sessionToken)

        Span.current().apply {
            setAttribute("auth.session_valid", session.valid)
            setAttribute("auth.customer_tier", "STANDARD")
        }

        log.info("Auth validated: customerId={} valid={}", request.customerId, session.valid)

        return AuthValidateResponse(valid = session.valid, customerTier = "STANDARD")
    }
}
