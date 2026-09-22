package com.workshop.session

import com.workshop.common.SessionResponse
import io.opentelemetry.api.trace.Span
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sessions")
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "session")
class SessionController {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/{token}")
    fun getSession(@PathVariable token: String): SessionResponse {
        Thread.sleep(8)

        Span.current().setAttribute("session.token_prefix", token.take(8))

        val response = SessionResponse(
            token = token,
            customerId = "cust-derived-from-token",
            valid = true
        )

        log.info("Session lookup: tokenPrefix={} valid={}", token.take(8), response.valid)

        return response
    }
}
