package com.workshop.transfer

import com.workshop.common.*
import io.mockk.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled("Enable after applying solution.patch — see WORKSHOP.md")
class ComplianceFanoutTest {

    @Test
    fun `compliance screening uses a single batch call for all beneficiaries`() {
        val beneficiaries = listOf(
            Beneficiary("ben-1", "Acme Corp"),
            Beneficiary("ben-2", "Globex Ltd"),
            Beneficiary("ben-3", "Initech SA")
        )

        val supportClient = mockk<SupportClient>()
        every { supportClient.getCustomer(any()) } returns CustomerResponse(
            customerId = "cust-test", name = "Jane Doe", tier = "STANDARD",
            beneficiaries = beneficiaries
        )
        every { supportClient.getLimits(any()) } returns LimitsResponse(
            customerId = "cust-test", dailyLimitGbp = 50_000L, withinLimit = true
        )
        every { supportClient.screenComplianceBatch(any()) } returns ComplianceBatchResult(
            results = beneficiaries.map { ComplianceResult(it.id, cleared = true) }
        )

        val pricingClient = mockk<PricingClient>()
        every { pricingClient.calculate(any()) } returns PricingResponse(
            strategy = "SINGLE_ROUTE", routeCandidateCount = 1,
            bestRate = 1.08, provider = "provider-a", routeType = "LOCAL_PAYOUT"
        )

        val controller = TransferController(supportClient, pricingClient)
        controller.prepare(PrepareTransferRequest("EUR", "USD", "BANK_TRANSFER", 1000L))

        verify(exactly = 1) { supportClient.screenComplianceBatch(any()) }
        verify(exactly = 0) { supportClient.screenCompliance(any(), any()) }
    }
}
