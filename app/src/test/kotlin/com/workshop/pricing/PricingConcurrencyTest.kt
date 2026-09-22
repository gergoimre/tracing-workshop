package com.workshop.pricing

import com.workshop.common.FxRateResponse
import com.workshop.common.RouteCandidate
import com.workshop.common.RoutingCandidatesResponse
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

@Disabled("Enable after applying solution.patch — see WORKSHOP.md")
class PricingConcurrencyTest {

    private val fxDelayMs = 200L
    private val candidateCount = 3

    @Test
    fun `MULTI_ROUTE FX requests execute concurrently`() {
        val candidates = (1..candidateCount).map { i ->
            RouteCandidate("route-$i", "LOCAL_PAYOUT", "provider-$i")
        }

        val fxClient = mockk<FxClient>()
        every { fxClient.getRate(any(), any(), any()) } answers {
            Thread.sleep(fxDelayMs)
            FxRateResponse("EUR", "BRL", 5.42, "provider-a", "LOCAL_PAYOUT")
        }

        val routingClient = mockk<RoutingClient>()
        every {
            routingClient.getCandidates("EUR", "BRL", "BANK_TRANSFER", "10000_PLUS")
        } returns RoutingCandidatesResponse(candidates)

        val controller = PricingController(fxClient, routingClient)

        val elapsed = measureTime {
            controller.calculatePricing(
                com.workshop.common.PricingRequest("EUR", "BRL", "BANK_TRANSFER", "10000_PLUS")
            )
        }

        val maxAllowed = (fxDelayMs * 1.8).milliseconds
        assertTrue(
            elapsed < maxAllowed,
            "Expected FX requests to run concurrently (~${fxDelayMs}ms) but total was $elapsed."
        )
    }
}
