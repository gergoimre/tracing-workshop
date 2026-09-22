package com.workshop.pricing

import com.workshop.common.FxRateResponse
import com.workshop.common.RouteCandidate
import com.workshop.common.RoutingCandidatesResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled("Enable after applying solution.patch — see WORKSHOP.md")
class RouteCacheTest {

    @Test
    fun `identical routing parameters reuse cached candidates across requests`() {
        val candidate = RouteCandidate("route-1", "LOCAL_PAYOUT", "provider-a")

        val routingClient = mockk<RoutingClient>()
        every {
            routingClient.getCandidates("EUR", "USD", "BANK_TRANSFER", "BELOW_10000")
        } returns RoutingCandidatesResponse(listOf(candidate))

        val fxClient = mockk<FxClient>()
        every { fxClient.getRate(any(), any(), any()) } returns
            FxRateResponse("EUR", "USD", 1.08, "provider-a", "LOCAL_PAYOUT")

        val cache = RouteCache()
        val controller = PricingController(fxClient, routingClient, cache)
        val request = com.workshop.common.PricingRequest("EUR", "USD", "BANK_TRANSFER", "BELOW_10000")

        // Two requests with the same routing parameters but different requestIds
        // After the fix: both should use the same cache entry (requestId not in key)
        controller.calculatePricing(request, "request-A")
        controller.calculatePricing(request, "request-B")

        // Routing should only be called once — second request hits cache
        verify(exactly = 1) { routingClient.getCandidates(any(), any(), any(), any()) }
    }
}
