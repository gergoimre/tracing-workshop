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
            beneficiaries = listOf(
                Beneficiary("ben-1", "Acme Corp"),
                Beneficiary("ben-2", "Globex Ltd"),
                Beneficiary("ben-3", "Initech SA")
            )
        )
        log.info("Customer lookup: id={} beneficiaries={}", id, response.beneficiaries.size)
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
        Span.current().setAttribute("compliance.beneficiary_index", request.beneficiaryIndex.toLong())
        val result = ComplianceResult(beneficiaryId = request.beneficiaryId, cleared = true)
        log.info("Compliance screen: beneficiaryId={} cleared={}", request.beneficiaryId, result.cleared)
        return result
    }

    @PostMapping("/compliance/screenBatch")
    fun screenBatch(@RequestBody request: ComplianceScreenBatchRequest): ComplianceBatchResult {
        val results = request.beneficiaries.map { ComplianceResult(beneficiaryId = it.id, cleared = true) }
        log.info("Compliance batch screen: count={} allCleared={}", results.size, results.all { it.cleared })
        return ComplianceBatchResult(results)
    }

    @GetMapping("/routing/candidates")
    fun routingCandidates(
        @RequestParam sourceCurrency: String,
        @RequestParam targetCurrency: String,
        @RequestParam transferType: String,
        @RequestParam amountBucket: String
    ): RoutingCandidatesResponse {
        val candidates = if (
            sourceCurrency == "EUR" &&
            targetCurrency == "BRL" &&
            transferType == "BANK_TRANSFER" &&
            amountBucket == "10000_PLUS"
        ) {
            listOf(
                RouteCandidate("route-1", "LOCAL_PAYOUT", "provider-a"),
                RouteCandidate("route-2", "LOCAL_PAYOUT", "provider-b"),
                RouteCandidate("route-3", "SWIFT", "provider-c")
            )
        } else {
            listOf(RouteCandidate("route-1", "LOCAL_PAYOUT", "provider-a"))
        }
        log.info("Routing candidates: {}→{} candidates={}", sourceCurrency, targetCurrency, candidates.size)
        return RoutingCandidatesResponse(candidates)
    }
}
