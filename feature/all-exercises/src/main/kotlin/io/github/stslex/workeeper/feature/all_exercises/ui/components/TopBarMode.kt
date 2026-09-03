// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State.SelectionMode

/** Which of the two top bars is drawn. §26 "Selection mode": the bar is replaced **whole**. */
internal enum class TopBarMode { RESTING, SELECTION }

/**
 * The top bar's crossfade key. GUARD: key on the mode, never on the rendered title — «Выбрано N»
 * is a count, and animating it reads a number wrong at every intermediate frame.
 */
internal fun topBarMode(selectionMode: SelectionMode): TopBarMode = when (selectionMode) {
    is SelectionMode.On -> TopBarMode.SELECTION
    SelectionMode.Off -> TopBarMode.RESTING
}
