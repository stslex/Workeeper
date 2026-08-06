// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.snackbar

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class SnackbarManagerTest {

    /**
     * Regression for the silent-drop bug: the real collector (`App.kt`) suspends inside
     * `SnackbarHostState.showSnackbar` for the whole time a snackbar is visible. A second
     * event emitted during that window must be buffered and delivered, not dropped. With a
     * zero-buffer `MutableSharedFlow` the second `tryEmit` returned `false` and the message
     * vanished — exactly the "no snackbar appears" symptom on the all-exercises blocked
     * bulk-archive path.
     */
    @Test
    fun `emissions while the collector is busy are buffered, not dropped`() = runTest {
        val received = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            SnackbarManager.snackbar.collect { model ->
                received += model.message
                // Mirror the App.kt collector suspending while the snackbar is shown.
                delay(SNACKBAR_VISIBLE_MILLIS)
            }
        }
        runCurrent() // let the collector subscribe before the first emission

        SnackbarManager.showSnackbar("first")
        // Emitted while the collector is still suspended showing "first".
        SnackbarManager.showSnackbar("second")

        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("first", "second"), received)
    }

    /**
     * The burst case the queue's KDoc forbids reintroducing: a deferred delete's model can
     * sit queued behind a visible toast while a burst of newer feedback arrives, and an
     * eviction there is a confirmed delete that silently never runs (its screen already
     * popped). Every entry must survive the burst, in order — a capped queue with any
     * overflow policy reds this.
     */
    @Test
    fun `a commit queued behind a burst is delivered, never evicted`() = runTest {
        val received = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            SnackbarManager.snackbar.collect { model ->
                received += model.message
                delay(SNACKBAR_VISIBLE_MILLIS)
            }
        }
        runCurrent()

        SnackbarManager.showSnackbar("visible")
        SnackbarManager.showSnackbar("deferred-commit")
        repeat(BURST_SIZE) { index -> SnackbarManager.showSnackbar("burst-$index") }

        advanceUntilIdle()
        job.cancel()

        assertEquals(
            listOf("visible", "deferred-commit") + List(BURST_SIZE) { "burst-$it" },
            received,
        )
    }

    private companion object {
        const val SNACKBAR_VISIBLE_MILLIS = 1_000L

        /** One past the 16-slot buffer the queue must NOT have. */
        const val BURST_SIZE = 17
    }
}
