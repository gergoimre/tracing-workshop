package com.workshop.transfer

import com.workshop.common.PricingResponse
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "transfer")
class BackgroundJobService(
    private val ledgerCommitClient: LedgerCommitClient,
    private val notificationClient: NotificationClient,
    private val auditClient: AuditClient,
    @Value("\${workshop.settle-delay-ms:500}") private val settleDelayMs: Long,
    @Value("\${workshop.reconcile-delay-ms:2000}") private val reconcileDelayMs: Long,
    @Value("\${workshop.receipt-delay-ms:4000}") private val receiptDelayMs: Long
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async("backgroundJobExecutor")
    @WithSpan("transfer.settle.async")
    fun settle(transferId: String, sourceCurrency: String, amount: Long, provider: String) {
        Thread.sleep(settleDelayMs)

        Span.current().apply {
            setAttribute("job.type", "settle")
            setAttribute("job.transfer_id", transferId)
        }

        log.info("Background settle started: transferId={}", transferId)

        val commit = ledgerCommitClient.commit(
            transferId = transferId,
            sourceCurrency = sourceCurrency,
            amount = amount,
            provider = provider
        )

        auditClient.record(
            eventType = "TRANSFER_SETTLED",
            entityId = transferId,
            actorId = "system",
            payload = """{"commitId":"${commit.commitId}","settled":${commit.settled}}"""
        )

        Span.current().setAttribute("job.commit_id", commit.commitId)
        log.info("Background settle complete: transferId={} commitId={}", transferId, commit.commitId)
    }

    @Async("backgroundJobExecutor")
    @WithSpan("transfer.reconcile.async")
    fun reconcile(transferId: String, sourceCurrency: String, targetCurrency: String) {
        Thread.sleep(reconcileDelayMs)

        Span.current().apply {
            setAttribute("job.type", "reconcile")
            setAttribute("job.transfer_id", transferId)
            setAttribute("job.source_currency", sourceCurrency)
            setAttribute("job.target_currency", targetCurrency)
        }

        log.info("Background reconcile started: transferId={}", transferId)

        auditClient.record(
            eventType = "TRANSFER_RECONCILED",
            entityId = transferId,
            actorId = "system",
            payload = """{"sourceCurrency":"$sourceCurrency","targetCurrency":"$targetCurrency","status":"MATCHED"}"""
        )

        log.info("Background reconcile complete: transferId={}", transferId)
    }

    @Async("backgroundJobExecutor")
    @WithSpan("transfer.receipt.async")
    fun sendReceipt(transferId: String, customerId: String) {
        Thread.sleep(receiptDelayMs)

        Span.current().apply {
            setAttribute("job.type", "receipt")
            setAttribute("job.transfer_id", transferId)
            setAttribute("job.customer_id", customerId)
        }

        log.info("Background receipt started: transferId={} customerId={}", transferId, customerId)

        val notification = notificationClient.send(
            customerId = customerId,
            transferId = transferId,
            channel = "PUSH",
            templateId = "transfer-receipt-v1"
        )

        auditClient.record(
            eventType = "RECEIPT_SENT",
            entityId = transferId,
            actorId = customerId,
            payload = """{"notificationId":"${notification.notificationId}","channel":"PUSH"}"""
        )

        Span.current().setAttribute("job.notification_id", notification.notificationId)
        log.info("Background receipt complete: transferId={} notificationId={}", transferId, notification.notificationId)
    }
}
