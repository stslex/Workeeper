// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.store

import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State.Mode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies that the `Screen.Training(uuid)` route argument flows through
 * `State.create(uuid)` into the Store's initial state. The Store's
 * `@Assisted screen: Screen.Training` constructor parameter (see
 * `SingleTrainingStoreImpl`) calls `State.create(uuid = screen.uuid)`.
 */
internal class SingleTrainingStateRouteArgTest {

    @Test
    fun `null uuid yields create-mode initial state with no loading`() {
        val state = State.create(uuid = Screen.Training(uuid = null).uuid)

        assertNull(state.uuid)
        assertEquals(Mode.Edit(isCreate = true), state.mode)
        assertFalse(state.isLoading)
    }

    @Test
    fun `non-null uuid yields read-mode initial state and starts loading`() {
        val screen = Screen.Training(uuid = "training-9")

        val state = State.create(uuid = screen.uuid)

        assertEquals("training-9", state.uuid)
        assertEquals(Mode.Read, state.mode)
        assertTrue(state.isLoading)
    }
}
