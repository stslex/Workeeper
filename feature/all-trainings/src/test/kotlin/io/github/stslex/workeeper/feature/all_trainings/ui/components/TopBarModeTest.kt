// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State.SelectionMode
import kotlinx.collections.immutable.persistentSetOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The top bar crossfades on mode flip and must sit still while the count changes. The "different
 * selections are one mode" case is the gate — no golden can express it.
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
        // Deselecting the last row does not leave the mode (§26), so no crossfade through zero.
        assertEquals(
            TopBarMode.SELECTION,
            topBarMode(SelectionMode.On(persistentSetOf())),
        )
    }
}
