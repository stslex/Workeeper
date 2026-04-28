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
) {

    val weightLabel: String
        get() = weight?.toDisplayLabel().orEmpty()

    private fun Double.toDisplayLabel(): String = if (this % 1.0 == 0.0) {
        toLong().toString()
    } else {
        toString()
    }
}
