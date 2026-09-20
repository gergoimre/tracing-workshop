package com.workshop.transfer

import com.workshop.common.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Verifies that compliance screening uses a single batch call instead of one
 * call per beneficiary.
 *
 * On the buggy implementation (map { screenCompliance(it) }) this test fails
 * because screenCompliance is called N times and screenComplianceBatch zero times.
 * After the fix (screenComplianceBatch) it passes.
 *
 * TODO (workshop): Enable this test after applying the fix from solution.patch.
 *   The test will fail on the buggy code and pass after the fix.
 */
@Disabled("Enable after applying solution.patch — see WORKSHOP.md Step 9")
class ComplianceFanoutTest {

    @Test
    fun `compliance screening uses a single batch call for all beneficiaries`() = runTest {
        val beneficiaries = listOf(
            Beneficiary("ben-1", "Acme Corp"),
            Beneficiary("ben-2", "Globex Ltd"),
            Beneficiary("ben-3", "Initech SA")
        )

        val supportClient = mockk<SupportClient>()
        coEvery { supportClient.getCustomer(any()) } returns CustomerResponse(
            customerId = "cust-test",
            name = "Jane Doe",
            tier = "STANDARD",
            beneficiaries = beneficiaries
        )
        coEvery { supportClient.getLimits(any()) } returns LimitsResponse(
            customerId = "cust-test",
            dailyLimitGbp = 50_000L,
            withinLimit = true
        )
        coEvery { supportClient.screenComplianceBatch(any()) } returns ComplianceBatchResult(
            results = beneficiaries.map { ComplianceResult(it.id, cleared = true) }
        )

        val pricingClient = mockk<PricingClient>()
        coEvery { pricingClient.calculate(any()) } returns PricingResponse(
            strategy = "SINGLE_ROUTE",
            routeCandidateCount = 1,
            bestRate = 1.08,
            provider = "provider-a",
            routeType = "LOCAL_PAYOUT"
        )

        val controller = TransferController(supportClient, pricingClient)
        controller.prepare(
            PrepareTransferRequest("EUR", "USD", "BANK_TRANSFER", 1000L)
        )

        // Batch endpoint called exactly once
        coVerify(exactly = 1) { supportClient.screenComplianceBatch(any()) }
        // Single endpoint called zero times
        coVerify(exactly = 0) { supportClient.screenCompliance(any(), any()) }
    }
}
