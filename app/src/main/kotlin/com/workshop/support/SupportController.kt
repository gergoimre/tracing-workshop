package com.workshop.support

import com.workshop.common.*
import io.opentelemetry.api.trace.Span
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.*

@RestController
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "support")
class SupportController {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/customer/{id}")
    fun getCustomer(@PathVariable id: String): CustomerResponse {
        val response = CustomerResponse(
            customerId = id,
            name = "Jane Doe",
            tier = "STANDARD",
            savedRecipients = listOf(
                Recipient("rec-1", "Acme Corp",  "BR1234567890"),
                Recipient("rec-2", "Globex Ltd", "BR0987654321"),
                Recipient("rec-3", "Initech SA", "BR1122334455")
            )
        )
        log.info("Customer lookup: id={}", id)
        return response
    }

    @GetMapping("/limits/{customerId}")
    fun getLimits(@PathVariable customerId: String): LimitsResponse {
        val response = LimitsResponse(customerId = customerId, dailyLimitGbp = 50_000L, withinLimit = true)
        log.info("Limits check: customerId={} withinLimit={}", customerId, response.withinLimit)
        return response
    }

    @PostMapping("/compliance/screen")
    fun screenSingle(@RequestBody request: ComplianceScreenRequest): ComplianceResult {
        Span.current().setAttribute("compliance.recipient_id", request.recipientId)
        val result = ComplianceResult(recipientId = request.recipientId, cleared = true)
        log.info("Compliance screen: recipientId={} cleared={}", request.recipientId, result.cleared)
        return result
    }

    @GetMapping("/routing/candidates")
    fun routingCandidates(
        @RequestParam sourceCurrency: String,
        @RequestParam targetCurrency: String,
        @RequestParam transferType: String,
        @RequestParam amount: Long
    ): RoutingCandidatesResponse {
        val candidates = if (
            sourceCurrency == "EUR" &&
            targetCurrency == "BRL" &&
            transferType == "BANK_TRANSFER" &&
            amount >= 10_000
        ) {
            listOf(
                RouteCandidate("route-1", "LOCAL_PAYOUT", "provider-a"),
                RouteCandidate("route-2", "LOCAL_PAYOUT", "provider-b"),
                RouteCandidate("route-3", "SWIFT",        "provider-c")
            )
        } else {
            listOf(RouteCandidate("route-1", "LOCAL_PAYOUT", "provider-a"))
        }
        log.info("Routing candidates: {}→{} candidates={}", sourceCurrency, targetCurrency, candidates.size)
        return RoutingCandidatesResponse(candidates)
    }
}
