// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State.Mode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies that the `Screen.Exercise` route argument flows through `State.create(uuid)`
 * into the Store's initial state. The Store's `@Assisted screen: Screen.Exercise`
 * constructor parameter (see `ExerciseStoreImpl`) calls `State.create(uuid = screen.uuid)`,
 * so this test pins the contract independent of the assisted-injection plumbing.
 */
internal class ExerciseStateRouteArgTest {

    @Test
    fun `null uuid yields create-mode initial state with no loading flag`() {
        val state = State.create(uuid = Screen.Exercise(uuid = null).uuid)

        assertNull(state.uuid)
        assertEquals(Mode.Edit(isCreate = true), state.mode)
        assertFalse(state.isLoading)
    }

    @Test
    fun `non-null uuid yields read-mode initial state and starts loading`() {
        val screen = Screen.Exercise(uuid = "ex-42")

        val state = State.create(uuid = screen.uuid)

        assertEquals("ex-42", state.uuid)
        assertEquals(Mode.Read, state.mode)
        assertTrue(state.isLoading)
    }
}
