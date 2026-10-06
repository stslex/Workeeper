// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * wear-live-sync.md §7.3 and §10.1: O6's token bucket. A token taken in the middle of a refill
 * period leaves that period running from where it started.
 */
internal class PhoneChangeLimiterTest {

    private val scope = CoroutineScope(Job())
    private val limiter = PhoneChangeLimiter(scope)

    @AfterEach
    fun tearDown() {
        scope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun `a partial refill period is kept across a take`() {
        repeat(PHONE_CHANGE_BURST) {
            assertEquals(0L, limiter.msUntilToken(T0))
            limiter.take(T0)
        }
        assertEquals(PHONE_CHANGE_REFILL_MS, limiter.msUntilToken(T0), "empty for one period after ten takes")

        // t0 + 15 s: one token came back at t0 + 10 s, and the period after it runs since then.
        val midPeriod = T0 + PHONE_CHANGE_REFILL_MS + PHONE_CHANGE_REFILL_MS / 2
        assertEquals(0L, limiter.msUntilToken(midPeriod))
        limiter.take(midPeriod)

        assertEquals(
            T0 + 2 * PHONE_CHANGE_REFILL_MS - midPeriod,
            limiter.msUntilToken(midPeriod),
            "the next token at t0 + 20 s, not one whole period after the take",
        )
    }

    private companion object {
        const val T0 = 1_000L
    }
}
