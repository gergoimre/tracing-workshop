package com.workshop.common

data class PrepareTransferRequest(
    val sourceCurrency: String,
    val targetCurrency: String,
    val transferType: String,
    val amount: Long
)

data class PrepareTransferResponse(
    val transferId: String,
    val sourceCurrency: String,
    val targetCurrency: String,
    val transferType: String,
    val amount: Long,
    val amountBucket: String,
    val pricingStrategy: String,
    val fxRate: Double,
    val status: String = "PREPARED"
)

data class RouteCandidate(
    val routeId: String,
    val routeType: String,
    val provider: String
)

data class FxRateRequest(
    val sourceCurrency: String,
    val targetCurrency: String,
    val provider: String,
    val routeType: String
)

data class FxRateResponse(
    val sourceCurrency: String,
    val targetCurrency: String,
    val rate: Double,
    val provider: String,
    val routeType: String
)

data class Beneficiary(
    val id: String,
    val name: String
)

data class ComplianceScreenRequest(
    val beneficiaryId: String,
    val beneficiaryIndex: Int
)

data class ComplianceScreenBatchRequest(
    val beneficiaries: List<Beneficiary>
)

data class ComplianceResult(
    val beneficiaryId: String,
    val cleared: Boolean
)

data class ComplianceBatchResult(
    val results: List<ComplianceResult>
)

data class CustomerResponse(
    val customerId: String,
    val name: String,
    val tier: String,
    val beneficiaries: List<Beneficiary>
)

data class LimitsResponse(
    val customerId: String,
    val dailyLimitGbp: Long,
    val withinLimit: Boolean
)

data class RoutingCandidatesResponse(
    val candidates: List<RouteCandidate>
)

data class PricingRequest(
    val sourceCurrency: String,
    val targetCurrency: String,
    val transferType: String,
    val amountBucket: String
)

data class PricingResponse(
    val strategy: String,
    val routeCandidateCount: Int,
    val bestRate: Double,
    val provider: String,
    val routeType: String
)
