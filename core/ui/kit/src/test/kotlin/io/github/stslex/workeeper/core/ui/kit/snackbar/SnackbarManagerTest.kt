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

    private companion object {
        const val SNACKBAR_VISIBLE_MILLIS = 1_000L
    }
}
