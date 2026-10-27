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
     * A visible row the user never filled in; UI-only, and discarded at session finish rather
     * than counted as work. See the v3 redesign spec §6.1.
     */
    val isUnfilled: Boolean get() = reps <= 0 && !isDone

    private fun Double.toDisplayLabel(): String = if (this % 1.0 == 0.0) {
        toLong().toString()
    } else {
        toString()
    }
}
