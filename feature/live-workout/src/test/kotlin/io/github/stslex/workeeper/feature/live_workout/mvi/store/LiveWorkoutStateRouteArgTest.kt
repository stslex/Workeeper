// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.store

import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Verifies that `Screen.LiveWorkout(sessionUuid, trainingUuid)` route arguments flow
 * through `State.create(...)` into the Store's initial state. The Store's
 * `@Assisted screen: Screen.LiveWorkout` constructor parameter (see
 * `LiveWorkoutStoreImpl`) calls
 * `State.create(sessionUuid = screen.sessionUuid, trainingUuid = screen.trainingUuid)`.
 *
 * Both arguments are nullable on `Screen.LiveWorkout` because the screen has three
 * entry modes:
 *   - sessionUuid != null              → resume existing session
 *   - sessionUuid == null && training != null → start fresh session for training
 *   - sessionUuid == null && training == null → blank-init adhoc session
 */
internal class LiveWorkoutStateRouteArgTest {

    @Test
    fun `resume entry preserves the session uuid and clears training uuid`() {
        val screen = Screen.LiveWorkout(sessionUuid = "session-7", trainingUuid = null)

        val state = State.create(
            sessionUuid = screen.sessionUuid,
            trainingUuid = screen.trainingUuid,
        )

        assertEquals("session-7", state.sessionUuid)
        assertNull(state.trainingUuid)
    }

    @Test
    fun `fresh-from-training entry preserves the training uuid and clears session uuid`() {
        val screen = Screen.LiveWorkout(sessionUuid = null, trainingUuid = "training-3")

        val state = State.create(
            sessionUuid = screen.sessionUuid,
            trainingUuid = screen.trainingUuid,
        )

        assertNull(state.sessionUuid)
        assertEquals("training-3", state.trainingUuid)
    }

    @Test
    fun `blank-init adhoc entry leaves both uuids null for downstream session creation`() {
        val screen = Screen.LiveWorkout(sessionUuid = null, trainingUuid = null)

        val state = State.create(
            sessionUuid = screen.sessionUuid,
            trainingUuid = screen.trainingUuid,
        )

        assertNull(state.sessionUuid)
        assertNull(state.trainingUuid)
    }

    @Test
    fun `both uuids non-null are forwarded to state verbatim`() {
        val screen = Screen.LiveWorkout(sessionUuid = "session-1", trainingUuid = "training-1")

        val state = State.create(
            sessionUuid = screen.sessionUuid,
            trainingUuid = screen.trainingUuid,
        )

        assertEquals("session-1", state.sessionUuid)
        assertEquals("training-1", state.trainingUuid)
    }
}
