// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.sheet

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.kit.components.setchip.SetType

@Stable
data class PlanEditorSet(
    val weight: Double?,
    val reps: Int,
    val type: SetType,
)
