// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.core.wear.protocol.NumericField

internal sealed interface ControllerAction {
    data class AdjustDraft(val field: NumericField, val steps: Int) : ControllerAction
    data class SetReps(val value: Int) : ControllerAction
    data class SetWeight(val value: Int?) : ControllerAction
    data object CompleteSet : ControllerAction
    data object Retry : ControllerAction
}
