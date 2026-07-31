// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State.SelectionMode

/** Which of the two top bars is drawn. §26 "Selection mode": the bar is replaced **whole**. */
internal enum class TopBarMode { RESTING, SELECTION }

/**
 * The top bar's crossfade key, extracted as a pure function **to make the exclusion assertable.**
 *
 * §26's continuity-motion row excludes **anything encoding a value** from the class, and the
 * selection bar's «Выбрано N» is a count. So the crossfade must key on the *mode* and never on the
 * title — keying it on the rendered title is the natural mistake, it compiles, it looks correct in
 * both endpoint goldens, and it animates the count on every single toggle, which is a number being
 * read wrong at every intermediate frame.
 *
 * Nothing about that is visible to a picture. Lifting the key into this function is what lets
 * `TopBarModeTest` assert the property directly: two different selections must map to the **same**
 * mode. That test fails the moment the count re-enters the transition, which is the only gate this
 * exclusion can have.
 */
internal fun topBarMode(selectionMode: SelectionMode): TopBarMode = when (selectionMode) {
    is SelectionMode.On -> TopBarMode.SELECTION
    SelectionMode.Off -> TopBarMode.RESTING
}
