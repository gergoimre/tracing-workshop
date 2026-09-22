package com.workshop.common

data class PrepareTransferRequest(
    val sourceCurrency: String,
    val targetCurrency: String,
    val transferType: String,
    val amount: Long,
    val recipientId: String
)

data class PrepareTransferResponse(
    val transferId: String,
    val sourceCurrency: String,
    val targetCurrency: String,
    val transferType: String,
    val amount: Long,
    val amountBucket: String,
    val recipientId: String,
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

data class Recipient(
    val id: String,
    val name: String,
    val accountNumber: String
)

data class ComplianceScreenRequest(
    val recipientId: String
)

data class ComplianceResult(
    val recipientId: String,
    val cleared: Boolean
)

data class CustomerResponse(
    val customerId: String,
    val name: String,
    val tier: String,
    val savedRecipients: List<Recipient>
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
