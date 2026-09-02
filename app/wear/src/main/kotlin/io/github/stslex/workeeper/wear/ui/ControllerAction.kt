// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

internal sealed interface ControllerAction {
    data class SetReps(val value: Int) : ControllerAction
    data class SetWeight(val value: Int?) : ControllerAction
    data object CompleteSet : ControllerAction
    data object Retry : ControllerAction
}
