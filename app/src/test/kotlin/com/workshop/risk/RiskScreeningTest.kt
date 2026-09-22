package com.workshop.risk

import com.workshop.common.DeviceFingerprintResponse
import com.workshop.common.RiskScoreRequest
import com.workshop.common.ScreeningCheckResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled("Enable after applying solution.patch — see WORKSHOP.md")
class RiskScreeningTest {

    @Test
    fun `memo screening deduplicates terms before calling screening service`() {
        val deviceClient = mockk<DeviceClient>()
        every { deviceClient.fingerprint(any()) } returns
            DeviceFingerprintResponse(deviceId = "dev-test", trusted = true, os = "iOS")

        val screeningClient = mockk<ScreeningClient>()
        every { screeningClient.check(any(), any()) } returns
            ScreeningCheckResponse(term = "urgent", flagged = false)

        val controller = RiskController(deviceClient, screeningClient)

        val request = RiskScoreRequest(
            customerId = "cust-123",
            targetCurrency = "USD",
            amountBucket = "10000_PLUS",
            memo = "urgent, urgent, payment"
        )

        controller.scoreRequest(request)

        // After the fix: deduplicated unique non-blank terms — "urgent" and "payment" only
        verify(exactly = 2) { screeningClient.check(any(), any()) }
    }
}
