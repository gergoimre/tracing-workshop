package com.workshop.screening

import com.workshop.common.ScreeningCheckRequest
import com.workshop.common.ScreeningCheckResponse
import io.opentelemetry.api.trace.Span
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/screening")
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "screening")
class ScreeningController {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/check")
    fun check(@RequestBody request: ScreeningCheckRequest): ScreeningCheckResponse {
        Thread.sleep(10)

        Span.current().apply {
            setAttribute("screening.term", request.term)
            setAttribute("screening.flagged", false)
        }

        log.info("Screening check: term={} customerId={} flagged=false", request.term, request.customerId)

        return ScreeningCheckResponse(term = request.term, flagged = false)
    }
}
