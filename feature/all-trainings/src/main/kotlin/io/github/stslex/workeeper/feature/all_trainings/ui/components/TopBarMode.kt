// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State.SelectionMode

/** Which of the two top bars is drawn. §26 "Selection mode": the bar is replaced **whole**. */
internal enum class TopBarMode { RESTING, SELECTION }

/**
 * The top bar's crossfade key. Keys on the mode, never on the «Выбрано N» title — §26 excludes
 * anything encoding a value from continuity motion; `TopBarModeTest` is the gate.
 */
internal fun topBarMode(selectionMode: SelectionMode): TopBarMode = when (selectionMode) {
    is SelectionMode.On -> TopBarMode.SELECTION
    SelectionMode.Off -> TopBarMode.RESTING
}
