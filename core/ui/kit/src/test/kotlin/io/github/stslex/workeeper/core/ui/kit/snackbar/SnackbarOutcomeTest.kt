// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.snackbar

import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * ED11's window-close signal at its selector. The consumer, named per §27's discriminator:
 * `App.kt`'s snackbar collector calls [resolveSnackbarOutcome] on every toast, and the
 * exercise feature's deferred permanent delete rides `onDismissed` — so «`Отменить` never
 * commits» and «a closed window always commits» are exactly the two branches here.
 *
 * Each case asserts BOTH lambdas — the fired one fired once and the other not at all —
 * because the defect this routing exists to prevent is delete-AND-undo running together.
 */
internal class SnackbarOutcomeTest {

    private class Recorder {
        var actions = 0
        var dismissals = 0

        fun model() = AppSnackbarModel(
            message = "m",
            actionLabel = "undo",
            action = { actions++ },
            onDismissed = { dismissals++ },
        )
    }

    @Test
    fun `ActionPerformed fires the action and never the commit`() = runTest {
        val recorder = Recorder()
        resolveSnackbarOutcome(SnackbarResult.ActionPerformed, recorder.model())
        assertEquals(1, recorder.actions)
        assertEquals(0, recorder.dismissals)
    }

    @Test
    fun `Dismissed fires the commit and never the action`() = runTest {
        val recorder = Recorder()
        resolveSnackbarOutcome(SnackbarResult.Dismissed, recorder.model())
        assertEquals(0, recorder.actions)
        assertEquals(1, recorder.dismissals)
    }

    @Test
    fun `a timed-out show — null — is a dismissal, and commits`() = runTest {
        val recorder = Recorder()
        resolveSnackbarOutcome(null, recorder.model())
        assertEquals(0, recorder.actions)
        assertEquals(1, recorder.dismissals)
    }
}
