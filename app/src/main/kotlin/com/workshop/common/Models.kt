package com.workshop.common

data class PrepareTransferRequest(
    val sourceCurrency: String,
    val targetCurrency: String,
    val transferType: String,
    val amount: Long,
    val recipientId: String,
    val memo: String? = null
)

data class PrepareTransferResponse(
    val transferId: String,
    val sourceCurrency: String,
    val targetCurrency: String,
    val transferType: String,
    val amount: Long,
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
    val amount: Long
)

data class PricingResponse(
    val strategy: String,
    val routeCandidateCount: Int,
    val bestRate: Double,
    val provider: String,
    val routeType: String
)

// auth-service
data class AuthValidateRequest(
    val customerId: String,
    val sessionToken: String
)

data class AuthValidateResponse(
    val valid: Boolean,
    val customerTier: String
)

// session-store-service
data class SessionResponse(
    val token: String,
    val customerId: String,
    val valid: Boolean
)

// risk-service
data class RiskScoreRequest(
    val customerId: String,
    val targetCurrency: String,
    val amount: Long,
    val memo: String?
)

data class RiskScoreResponse(
    val score: Int,
    val band: String,
    val approved: Boolean
)

// device-service
data class DeviceFingerprintRequest(
    val customerId: String
)

data class DeviceFingerprintResponse(
    val deviceId: String,
    val trusted: Boolean,
    val os: String
)

// screening-service
data class ScreeningCheckRequest(
    val term: String,
    val customerId: String
)

data class ScreeningCheckResponse(
    val term: String,
    val flagged: Boolean
)

// ledger-service
data class LedgerReserveRequest(
    val transferId: String,
    val sourceCurrency: String,
    val amount: Long,
    val provider: String
)

data class LedgerReserveResponse(
    val reservationId: String,
    val accountId: String,
    val reserved: Boolean
)

// ledger-service (commit — used by async settlement job)
data class LedgerCommitRequest(
    val transferId: String,
    val sourceCurrency: String,
    val amount: Long,
    val provider: String
)

data class LedgerCommitResponse(
    val commitId: String,
    val settled: Boolean
)

// accounts-service
data class AccountBalanceResponse(
    val accountId: String,
    val currency: String,
    val balanceMinorUnits: Long,
    val sufficient: Boolean
)

// notification-service
data class NotificationRequest(
    val customerId: String,
    val transferId: String,
    val channel: String,
    val templateId: String
)

data class NotificationResponse(
    val notificationId: String,
    val channel: String,
    val queued: Boolean
)

// audit-service
data class AuditEventRequest(
    val eventType: String,
    val entityId: String,
    val actorId: String,
    val payload: String
)

data class AuditEventResponse(
    val eventId: String,
    val recorded: Boolean
)
