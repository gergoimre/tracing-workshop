package com.workshop.support

import com.workshop.common.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "support")
class SupportController {

    // ── Customer ──────────────────────────────────────────────────────────────

    @GetMapping("/customer/{id}")
    fun getCustomer(@PathVariable id: String): CustomerResponse =
        CustomerResponse(
            customerId = id,
            name = "Jane Doe",
            tier = "STANDARD",
            beneficiaries = listOf(
                Beneficiary("ben-1", "Acme Corp"),
                Beneficiary("ben-2", "Globex Ltd"),
                Beneficiary("ben-3", "Initech SA")
            )
        )

    // ── Limits ────────────────────────────────────────────────────────────────

    @GetMapping("/limits/{customerId}")
    fun getLimits(@PathVariable customerId: String): LimitsResponse =
        LimitsResponse(customerId = customerId, dailyLimitGbp = 50_000L, withinLimit = true)

    // ── Compliance — single (used by the buggy transfer service) ──────────────

    @PostMapping("/compliance/screen")
    fun screenSingle(@RequestBody request: ComplianceScreenRequest): ComplianceResult =
        ComplianceResult(beneficiaryId = request.beneficiaryId, cleared = true)

    // ── Compliance — batch (used after fix) ───────────────────────────────────

    @PostMapping("/compliance/screenBatch")
    fun screenBatch(@RequestBody request: ComplianceScreenBatchRequest): ComplianceBatchResult =
        ComplianceBatchResult(
            results = request.beneficiaries.map { ComplianceResult(beneficiaryId = it.id, cleared = true) }
        )

    // ── Routing candidates ────────────────────────────────────────────────────

    @GetMapping("/routing/candidates")
    fun routingCandidates(
        @RequestParam sourceCurrency: String,
        @RequestParam targetCurrency: String,
        @RequestParam transferType: String,
        @RequestParam amountBucket: String
    ): RoutingCandidatesResponse {
        // MULTI_ROUTE path: EUR→BRL BANK_TRANSFER 10000_PLUS gets 3 candidates
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
        return RoutingCandidatesResponse(candidates)
    }
}
