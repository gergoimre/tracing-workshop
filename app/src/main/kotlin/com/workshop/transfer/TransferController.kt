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
    private val supportClient: SupportClient,
    private val pricingClient: PricingClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/prepare")
    fun prepare(@RequestBody request: PrepareTransferRequest): PrepareTransferResponse {
        val bucket = amountBucket(request.amount)

        Span.current().apply {
            setAttribute("transfer.source_currency", request.sourceCurrency)
            setAttribute("transfer.target_currency", request.targetCurrency)
            setAttribute("transfer.type", request.transferType)
            setAttribute("transfer.amount_bucket", bucket)
            setAttribute("transfer.recipient_id", request.recipientId)
        }

        log.info("Preparing transfer: {}→{} {} recipient={} amount_bucket={}",
            request.sourceCurrency, request.targetCurrency, request.transferType,
            request.recipientId, bucket)

        val customer = supportClient.getCustomer("cust-${UUID.randomUUID().toString().take(8)}")
        log.info("Customer loaded: id={}", customer.customerId)

        val limits = supportClient.getLimits(customer.customerId)
        log.info("Limits checked: withinLimit={}", limits.withinLimit)

        val compliance = supportClient.screenCompliance(request.recipientId)
        log.info("Compliance cleared: recipientId={} cleared={}", request.recipientId, compliance.cleared)

        val pricing = pricingClient.calculate(
            PricingRequest(request.sourceCurrency, request.targetCurrency, request.transferType, bucket)
        )
        log.info("Pricing complete: strategy={} rate={}", pricing.strategy, pricing.bestRate)

        val transferId = "txn-${UUID.randomUUID()}"
        log.info("Transfer prepared: id={}", transferId)

        return PrepareTransferResponse(
            transferId = transferId,
            sourceCurrency = request.sourceCurrency,
            targetCurrency = request.targetCurrency,
            transferType = request.transferType,
            amount = request.amount,
            amountBucket = bucket,
            recipientId = request.recipientId,
            pricingStrategy = pricing.strategy,
            fxRate = pricing.bestRate
        )
    }
}
