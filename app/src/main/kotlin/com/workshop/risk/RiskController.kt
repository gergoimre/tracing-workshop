package com.workshop.risk

import com.workshop.common.RiskScoreRequest
import com.workshop.common.RiskScoreResponse
import io.micrometer.observation.annotation.Observed
import io.opentelemetry.api.trace.Span
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/risk")
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "risk")
class RiskController(
    private val deviceClient: DeviceClient,
    private val screeningClient: ScreeningClient
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/score")
    @Observed(name = "risk.score")
    fun score(@RequestBody request: RiskScoreRequest): RiskScoreResponse {
        Thread.sleep(15)

        val device = deviceClient.fingerprint(request.customerId)

        Span.current().apply {
            setAttribute("risk.device_trusted", device.trusted)
            setAttribute("risk.target_currency", request.targetCurrency)
            setAttribute("risk.amount", request.amount)
            if (request.memo != null) setAttribute("risk.memo", request.memo)
        }

        if (request.memo != null
            && request.targetCurrency == "USD"
            && request.amount >= 10_000
        ) {
            screenMemoTerms(request.memo, request.customerId)
        }

        val score = if (device.trusted) 12 else 45
        val band = if (score < 30) "LOW" else "MEDIUM"

        log.info("Risk scored: customerId={} score={} band={}", request.customerId, score, band)

        return RiskScoreResponse(score = score, band = band, approved = true)
    }

    private fun screenMemoTerms(memo: String, customerId: String) {
        val terms = memo.split(",")
            .flatMap { it.split(" ") }
            .map { it.trim().lowercase() }

        terms.forEach { term ->
            val result = screeningClient.check(term, customerId)
            log.info("Screening check: term={} flagged={}", term, result.flagged)
        }

        Span.current().setAttribute("risk.screening_terms_checked", terms.size.toLong())
    }
}
