// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State.SelectionMode
import kotlinx.collections.immutable.persistentSetOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Gates §26's exclusion: the bar crossfades on the mode flip and must sit still while the count
 * changes. No golden can see it — both endpoints are correct either way.
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
        // Deselecting the last row does not leave the mode, so the bar must not flip back and
        // cross-fade twice on a round trip through zero.
        assertEquals(
            TopBarMode.SELECTION,
            topBarMode(SelectionMode.On(persistentSetOf())),
        )
    }
}
