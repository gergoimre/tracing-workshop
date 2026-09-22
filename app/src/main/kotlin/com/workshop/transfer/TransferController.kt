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

        // The agent created the SERVER span. Attach business attributes to it.
        Span.current().apply {
            setAttribute("transfer.source_currency", request.sourceCurrency)
            setAttribute("transfer.target_currency", request.targetCurrency)
            setAttribute("transfer.type", request.transferType)
            setAttribute("transfer.amount_bucket", bucket)
        }

        log.info("Preparing transfer: {}→{} {} amount_bucket={}",
            request.sourceCurrency, request.targetCurrency, request.transferType, bucket)

        val customer = supportClient.getCustomer("cust-${UUID.randomUUID().toString().take(8)}")
        log.info("Customer loaded: id={} beneficiaries={}", customer.customerId, customer.beneficiaries.size)

        val limits = supportClient.getLimits(customer.customerId)
        log.info("Limits checked: withinLimit={}", limits.withinLimit)

        // ──────────────────────────────────────────────────────────────────
        // BUG #2: N+1 compliance fan-out.
        // Each beneficiary is screened with a separate HTTP call instead of
        // one batched call. This is always-on — every transfer goes through
        // this path regardless of currency or amount.
        //
        // The bug looks like ordinary per-item validation in code.
        // In logs: each call logs "Compliance cleared: true" — nothing
        //   reveals that N calls were made instead of 1.
        // In a trace: N identical sibling compliance.screen spans are
        //   immediately visible in the waterfall.
        //
        // The fix (in solution.patch) is:
        //   supportClient.screenComplianceBatch(customer.beneficiaries)
        // ──────────────────────────────────────────────────────────────────
        customer.beneficiaries.forEachIndexed { index, beneficiary ->
            val result = supportClient.screenCompliance(beneficiary.id, index)
            log.info("Compliance cleared: beneficiaryId={} cleared={}", beneficiary.id, result.cleared)
        }

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
            pricingStrategy = pricing.strategy,
            fxRate = pricing.bestRate
        )
    }
}
