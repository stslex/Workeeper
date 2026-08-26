// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.snackbar

import androidx.compose.ui.platform.AccessibilityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The toast's timeout and its accessibility consultation — invisible to a golden or a device. */
internal class ToastDurationTest {

    /** Records the call so the test can assert the manager was consulted, and with what. */
    private class RecordingManager(private val answer: Long) : AccessibilityManager {
        var calls = 0
        var lastOriginal: Long? = null
        var lastContainsControls: Boolean? = null

        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            containsIcons: Boolean,
            containsText: Boolean,
            containsControls: Boolean,
        ): Long {
            calls++
            lastOriginal = originalTimeoutMillis
            lastContainsControls = containsControls
            return answer
        }
    }

    @Test
    fun `with no accessibility manager the toast lasts exactly the drawn 5000ms`() {
        assertEquals(TOAST_VISIBLE_MS, toastTimeoutMillis(null, hasAction = true))
        assertEquals(5_000L, TOAST_VISIBLE_MS)
    }

    @Test
    fun `5000 is on neither M3 rung, which is why the host times this`() {
        // Asserted so the drawn number cannot be quietly rounded back onto an M3 rung.
        assertNotEquals(M3_SHORT_MS, TOAST_VISIBLE_MS)
        assertNotEquals(M3_LONG_MS, TOAST_VISIBLE_MS)
        assertTrue(TOAST_VISIBLE_MS in (M3_SHORT_MS + 1) until M3_LONG_MS)
    }

    @Test
    fun `the accessibility manager is consulted, and on the drawn number`() {
        // GUARD: the base must stay finite — an indefinite timeout short-circuits the manager
        // and silently ignores the user's display-timeout preference.
        val manager = RecordingManager(answer = 30_000L)

        val timeout = toastTimeoutMillis(manager, hasAction = true)

        assertEquals(1, manager.calls)
        assertEquals(TOAST_VISIBLE_MS, manager.lastOriginal)
        assertEquals(30_000L, timeout)
    }

    @Test
    fun `a stretched preference wins over the drawn number`() {
        val stretched = toastTimeoutMillis(RecordingManager(answer = 120_000L), hasAction = true)
        assertTrue(
            stretched > TOAST_VISIBLE_MS,
            "a display-timeout preference must be able to extend the toast; got $stretched",
        )
    }

    @Test
    fun `containsControls tracks whether the toast carries an action`() {
        // The platform grants more time to reach a control than to only read text.
        val withAction = RecordingManager(answer = TOAST_VISIBLE_MS)
        toastTimeoutMillis(withAction, hasAction = true)
        assertEquals(true, withAction.lastContainsControls)

        val withoutAction = RecordingManager(answer = TOAST_VISIBLE_MS)
        toastTimeoutMillis(withoutAction, hasAction = false)
        assertEquals(false, withoutAction.lastContainsControls)
    }

    private companion object {
        const val M3_SHORT_MS = 4_000L
        const val M3_LONG_MS = 10_000L
    }
}
