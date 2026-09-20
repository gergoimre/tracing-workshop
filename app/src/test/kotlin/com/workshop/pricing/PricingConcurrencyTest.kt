package com.workshop.pricing

import com.workshop.common.FxRateResponse
import com.workshop.common.RouteCandidate
import com.workshop.common.RoutingCandidatesResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

/**
 * Verifies that the pricing service fetches FX rates for multiple route
 * candidates concurrently instead of sequentially.
 *
 * On the buggy implementation (async { }.await() inside map) the total
 * duration is roughly N × FX_DELAY_MS.  After the fix (awaitAll) the
 * total duration is roughly 1 × FX_DELAY_MS regardless of N.
 *
 * TODO (workshop): Enable this test after applying the fix from solution.patch.
 *   The test will fail on the buggy code and pass after the fix.
 */
@Disabled("Enable after applying solution.patch — see WORKSHOP.md Step 9")
class PricingConcurrencyTest {

    private val fxDelayMs = 200L   // short delay so the test stays fast
    private val candidateCount = 3

    @Test
    fun `MULTI_ROUTE FX requests execute concurrently`() = runTest {
        val candidates = (1..candidateCount).map { i ->
            RouteCandidate("route-$i", "LOCAL_PAYOUT", "provider-$i")
        }

        val fxClient = mockk<FxClient>()
        coEvery { fxClient.getRate(any(), any(), any()) } coAnswers {
            kotlinx.coroutines.delay(fxDelayMs)
            FxRateResponse("EUR", "BRL", 5.42, "provider-a", "LOCAL_PAYOUT")
        }

        val routingClient = mockk<RoutingClient>()
        coEvery {
            routingClient.getCandidates("EUR", "BRL", "BANK_TRANSFER", "10000_PLUS")
        } returns RoutingCandidatesResponse(candidates)

        val controller = PricingController(fxClient, routingClient)

        val elapsed = measureTime {
            controller.calculate(
                com.workshop.common.PricingRequest("EUR", "BRL", "BANK_TRANSFER", "10000_PLUS")
            )
        }

        // If requests run sequentially: ~600ms. Concurrently: ~200ms.
        // Allow 1.8× the single delay as a generous upper bound.
        val maxAllowed = (fxDelayMs * 1.8).milliseconds
        assertTrue(elapsed < maxAllowed) {
            "Expected FX requests to run concurrently (~${fxDelayMs}ms) " +
            "but total was ${elapsed}. They appear to be sequential."
        }
    }
}
