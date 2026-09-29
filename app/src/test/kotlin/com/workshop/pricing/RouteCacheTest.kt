package com.workshop.pricing

import com.workshop.common.FxRateResponse
import com.workshop.common.LedgerReserveResponse
import com.workshop.common.RouteCandidate
import com.workshop.common.RoutingCandidatesResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class RouteCacheTest {

    @Test
    fun `identical routing parameters reuse cached candidates across requests`() {
        val candidate = RouteCandidate("route-1", "LOCAL_PAYOUT", "provider-a")

        val routingClient = mockk<RoutingClient>()
        every {
            routingClient.getCandidates("EUR", "USD", "BANK_TRANSFER", 5000L)
        } returns RoutingCandidatesResponse(listOf(candidate))

        val fxClient = mockk<FxClient>()
        every { fxClient.getRate(any(), any(), any()) } returns
            FxRateResponse("EUR", "USD", 1.08, "provider-a", "LOCAL_PAYOUT")

        val ledgerClient = mockk<LedgerClient>()
        every { ledgerClient.reserve(any(), any(), any(), any()) } returns
            LedgerReserveResponse("res-test", "acc-eur-primary", true)

        val cache = RouteCache()
        val controller = PricingController(fxClient, routingClient, cache, ledgerClient)
        val request = com.workshop.common.PricingRequest("EUR", "USD", "BANK_TRANSFER", 5000L)

        controller.calculate(request, "request-A")
        controller.calculate(request, "request-B")

        verify(exactly = 1) { routingClient.getCandidates(any(), any(), any(), any()) }
    }
}
