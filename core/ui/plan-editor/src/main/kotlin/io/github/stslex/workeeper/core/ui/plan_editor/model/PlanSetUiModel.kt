// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.model

import androidx.compose.runtime.Stable

@Stable
data class PlanSetUiModel(
    val weight: Double?,
    val reps: Int,
    val type: SetTypeUiModel,
)