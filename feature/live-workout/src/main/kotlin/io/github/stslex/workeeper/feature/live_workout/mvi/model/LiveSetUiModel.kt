// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel

@Stable
data class LiveSetUiModel(
    val position: Int,
    val weight: Double?,
    val reps: Int,
    val type: SetTypeUiModel,
    val isDone: Boolean,
    val isPersonalRecord: Boolean = false,
) {

    val weightLabel: String get() = weight?.toDisplayLabel().orEmpty()

    /**
     * A row the user can see but has not filled in: `reps` is the "not entered" sentinel and
     * the row was never marked done. `LiveSetRow` renders it as an empty reps field via
     * `reps.takeIf { it > 0 }`.
     *
     * These are produced by `LiveSetRowsResolver`'s fallback row when a plan is shorter than
     * the visible row count. They are UI-only — see `FinishResult.discardedUnfilledSets` for
     * why none of them ever reaches the database — and they are discarded at session finish
     * rather than being counted as work.
     *
     * ### Why discarding is safe regardless of intent
     *
     * A zero-rep set renders as an empty field and can hold no personal record, so a
     * "deliberate zero" is indistinguishable from an unfilled one both in the data and on
     * screen. There is no user intent that discarding could destroy: the two cases display
     * identically and behave identically. The alternative — keeping them — makes the progress
     * rail count sets that never happened, and §1's whole expressive device rests on that
     * count being honest.
     */
    val isUnfilled: Boolean get() = reps <= 0 && !isDone

    private fun Double.toDisplayLabel(): String = if (this % 1.0 == 0.0) {
        toLong().toString()
    } else {
        toString()
    }
}
