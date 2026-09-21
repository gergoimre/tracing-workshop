package com.workshop.transfer

import com.workshop.common.*
import io.opentelemetry.api.trace.Span
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

    @PostMapping("/prepare")
    suspend fun prepare(@RequestBody request: PrepareTransferRequest): PrepareTransferResponse {
        val bucket = amountBucket(request.amount)

        // The OTel agent already created the root SERVER span for this HTTP request.
        // We just enrich it with business attributes — no new span needed.
        val span = Span.current()
        span.setAttribute("transfer.source_currency", request.sourceCurrency)
        span.setAttribute("transfer.target_currency", request.targetCurrency)
        span.setAttribute("transfer.type", request.transferType)
        span.setAttribute("transfer.amount_bucket", bucket)

        // 1. Customer lookup
        val customer = supportClient.getCustomer("cust-${UUID.randomUUID().toString().take(8)}")

        // 2. Limits check
        supportClient.getLimits(customer.customerId)

        // ──────────────────────────────────────────────────────────────────
        // BUG #2: N+1 compliance fan-out.
        // Each beneficiary is screened with a separate HTTP call instead of
        // one batched call. This is always-on — every transfer goes through
        // this path regardless of currency or amount.
        //
        // The bug looks like ordinary per-item validation in code.
        // In logs: N separate 200 OK responses, nothing anomalous.
        // In a trace: N identical sibling compliance.screen spans are
        // immediately visible in the waterfall.
        //
        // The fix (in solution.patch) is:
        //   supportClient.screenComplianceBatch(customer.beneficiaries)
        // ──────────────────────────────────────────────────────────────────
        customer.beneficiaries.mapIndexed { index, beneficiary ->
            supportClient.screenCompliance(beneficiary.id, index)
        }

        // 4. Pricing
        val pricing = pricingClient.calculate(
            PricingRequest(
                sourceCurrency = request.sourceCurrency,
                targetCurrency = request.targetCurrency,
                transferType = request.transferType,
                amountBucket = bucket
            )
        )

        return PrepareTransferResponse(
            transferId = "txn-${UUID.randomUUID()}",
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
