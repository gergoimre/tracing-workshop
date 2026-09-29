package com.workshop.transfer

import com.workshop.common.*
import io.opentelemetry.api.trace.Span
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/transfers")
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "transfer")
class TransferController(
    private val authClient: AuthClient,
    private val supportClient: SupportClient,
    private val riskClient: RiskClient,
    private val pricingClient: PricingClient,
    private val notificationClient: NotificationClient,
    private val auditClient: AuditClient,
    private val backgroundJobService: BackgroundJobService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/prepare")
    fun prepare(@RequestBody request: PrepareTransferRequest): PrepareTransferResponse {
        val sessionToken = "sess-${request.recipientId}-token"

        Span.current().apply {
            setAttribute("transfer.source_currency", request.sourceCurrency)
            setAttribute("transfer.target_currency", request.targetCurrency)
            setAttribute("transfer.type", request.transferType)
            setAttribute("transfer.amount", request.amount)
            setAttribute("transfer.recipient_id", request.recipientId)
            setAttribute("transfer.has_memo", request.memo != null)
            if (request.memo != null) setAttribute("transfer.memo", request.memo)
        }

        log.info("Preparing transfer: {}→{} {} recipient={} amount={} memo={}",
            request.sourceCurrency, request.targetCurrency, request.transferType,
            request.recipientId, request.amount, request.memo != null)

        val customerId = "cust-${UUID.randomUUID().toString().take(8)}"

        val auth = authClient.validate(customerId, sessionToken)
        log.info("Auth validated: valid={} tier={}", auth.valid, auth.customerTier)

        val customer = supportClient.getCustomer(customerId)
        log.info("Customer loaded: id={}", customer.customerId)

        val limits = supportClient.getLimits(customer.customerId)
        log.info("Limits checked: withinLimit={}", limits.withinLimit)

        val compliance = supportClient.screenCompliance(request.recipientId)
        log.info("Compliance cleared: recipientId={} cleared={}", request.recipientId, compliance.cleared)

        val risk = riskClient.score(
            customerId = customer.customerId,
            targetCurrency = request.targetCurrency,
            amount = request.amount,
            memo = request.memo
        )
        log.info("Risk scored: score={} band={}", risk.score, risk.band)

        val pricing = pricingClient.calculate(
            PricingRequest(request.sourceCurrency, request.targetCurrency, request.transferType, request.amount)
        )
        log.info("Pricing complete: strategy={} rate={}", pricing.strategy, pricing.bestRate)

        val transferId = "txn-${UUID.randomUUID()}"

        val notification = notificationClient.send(
            customerId = customer.customerId,
            transferId = transferId,
            channel = "EMAIL",
            templateId = "transfer-prepared-v2"
        )
        log.info("Notification queued: notificationId={}", notification.notificationId)

        auditClient.record(
            eventType = "TRANSFER_PREPARED",
            entityId = transferId,
            actorId = customer.customerId,
            payload = """{"sourceCurrency":"${request.sourceCurrency}","targetCurrency":"${request.targetCurrency}","amount":${request.amount}}"""
        )
        log.info("Transfer prepared and audited: id={}", transferId)

        Span.current().setAttribute("transfer.async_jobs_started", 3L)

        backgroundJobService.settle(
            transferId = transferId,
            sourceCurrency = request.sourceCurrency,
            amount = request.amount,
            provider = pricing.provider
        )
        backgroundJobService.reconcile(
            transferId = transferId,
            sourceCurrency = request.sourceCurrency,
            targetCurrency = request.targetCurrency
        )
        backgroundJobService.sendReceipt(
            transferId = transferId,
            customerId = customer.customerId
        )

        log.info("Background jobs dispatched: transferId={}", transferId)

        return PrepareTransferResponse(
            transferId = transferId,
            sourceCurrency = request.sourceCurrency,
            targetCurrency = request.targetCurrency,
            transferType = request.transferType,
            amount = request.amount,
            recipientId = request.recipientId,
            pricingStrategy = pricing.strategy,
            fxRate = pricing.bestRate
        )
    }
}
