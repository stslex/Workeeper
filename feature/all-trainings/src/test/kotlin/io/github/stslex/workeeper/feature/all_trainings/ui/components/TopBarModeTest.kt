// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State.SelectionMode
import kotlinx.collections.immutable.persistentSetOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The top bar's crossfade key — and the one assertion that gates §26's **exclusion**.
 *
 * The continuity-motion row admits motion whose job is that nothing teleports, and excludes
 * anything encoding a value. «Выбрано N» is a value. The bar therefore crossfades when the *mode*
 * flips and must sit perfectly still while the count changes underneath it.
 *
 * There is no picture that can check this. Both endpoint goldens are correct either way; the
 * defect is a transition firing on an input it should be blind to, which a single frame cannot
 * express. The "different selections are one mode" case below is the whole gate: it goes red the
 * moment the count re-enters the transition key.
 */
internal class TopBarModeTest {

    @Test
    fun `off is the resting bar`() {
        assertEquals(TopBarMode.RESTING, topBarMode(SelectionMode.Off))
    }

    @Test
    fun `on is the selection bar`() {
        assertEquals(
            TopBarMode.SELECTION,
            topBarMode(SelectionMode.On(persistentSetOf("a"))),
        )
    }

    @Test
    fun `different selections are one mode, so the count cannot drive the crossfade`() {
        val one = topBarMode(SelectionMode.On(persistentSetOf("a")))
        val three = topBarMode(SelectionMode.On(persistentSetOf("a", "b", "c")))
        assertEquals(one, three)
    }

    @Test
    fun `an empty selection is still the selection bar`() {
        // Deselecting the last row does not leave the mode — §26 "List states reached by an
        // action": «Отметки останутся, пока не выйдешь из режима». So the bar must not flip back
        // and cross-fade twice on a round trip through zero.
        assertEquals(
            TopBarMode.SELECTION,
            topBarMode(SelectionMode.On(persistentSetOf())),
        )
    }
}
