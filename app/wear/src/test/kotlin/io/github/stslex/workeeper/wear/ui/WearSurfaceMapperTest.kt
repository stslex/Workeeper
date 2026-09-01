// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import io.github.stslex.workeeper.core.wear.protocol.BoundedDisplayName
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.MutationUnavailableReason
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.PhoneActionReason
import io.github.stslex.workeeper.core.wear.protocol.ProtocolRejectionReason
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.wear.state.ActiveFreshness
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import io.github.stslex.workeeper.wear.state.WatchDisplayState
import io.github.stslex.workeeper.wear.state.WatchReducerState
import io.github.stslex.workeeper.wear.state.WatchWorkoutReducer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WearSurfaceMapperTest {

    @Test
    fun `maps every targetless read-only state without controls`() {
        val cases = listOf(
            WatchReducerState() to WearSurfaceKind.LOADING,
            WatchReducerState(
                display = WatchDisplayState.NoSession(ReducerTestFixtures.epoch),
            ) to WearSurfaceKind.NO_SESSION,
            phoneAction(
                PhoneActionReason.NoSetRows(
                    ReducerTestFixtures.exerciseA,
                    BoundedDisplayName.Value("Rows missing"),
                ),
            ) to WearSurfaceKind.PHONE_ACTION_NO_SETS,
            phoneAction(
                PhoneActionReason.UnsupportedNumericValues(
                    NumericField.WEIGHT,
                    ReducerTestFixtures.exerciseA,
                    BoundedDisplayName.Value("Unsupported"),
                ),
            ) to WearSurfaceKind.PHONE_ACTION_UNSUPPORTED,
            phoneAction(PhoneActionReason.PayloadTooLarge) to WearSurfaceKind.PAYLOAD_TOO_LARGE,
            workoutComplete() to WearSurfaceKind.WORKOUT_COMPLETE,
            WatchReducerState(
                display = WatchDisplayState.ProtocolMismatch(
                    ProtocolRejectionReason.COMMAND_FINGERPRINT_MISMATCH,
                ),
            ) to WearSurfaceKind.PROTOCOL_MISMATCH,
        )

        cases.forEach { (state, expected) ->
            val model = WearSurfaceMapper.map(state)
            assertEquals(expected, model.kind)
            assertFalse(model.controlsVisible)
            assertFalse(model.completeEnabled)
        }
    }

    @Test
    fun `fresh exact snapshot is actionable and unavailable remains visible read-only`() {
        val reducer = WatchWorkoutReducer()
        val request = ReducerTestFixtures.id(501)
        reducer.issueHandshake(request, 1_000)
        reducer.receiveSnapshot(request, ReducerTestFixtures.active(), 1_000)

        val fresh = WearSurfaceMapper.map(reducer.state)
        assertEquals(WearSurfaceKind.ACTIVE, fresh.kind)
        assertTrue(fresh.controlsVisible)
        assertTrue(fresh.controlsEnabled)
        assertTrue(fresh.completeEnabled)
        assertEquals(8, fresh.reps)
        assertEquals(10_000, fresh.weightHundredthsKg)

        val readOnly = WatchSurfaceState(
            snapshot = ReducerTestFixtures.active(unavailable = true),
            freshness = ActiveFreshness.REFRESH_REQUIRED,
        )
        val unavailable = WearSurfaceMapper.map(readOnly)
        assertEquals(WearSurfaceKind.REFRESH_REQUIRED, unavailable.kind)
        assertTrue(unavailable.controlsVisible)
        assertFalse(unavailable.controlsEnabled)
        assertFalse(unavailable.completeEnabled)
    }

    @Test
    fun `disconnected and omitted names preserve safe generic fallbacks`() {
        val source = ReducerTestFixtures.active()
        val payload = source.payload as SnapshotPayload.ActiveWithTarget
        val omitted = source.copy(
            payload = payload.copy(
                trainingName = BoundedDisplayName.Omitted(
                    io.github.stslex.workeeper.core.wear.protocol.OmissionReason.TOO_LARGE,
                ),
                target = payload.target.copy(
                    exerciseName = BoundedDisplayName.Omitted(
                        io.github.stslex.workeeper.core.wear.protocol.OmissionReason.INVALID_UNICODE,
                    ),
                ),
                mutationAuthority = MutationAuthority.Unavailable(
                    MutationUnavailableReason.FRESH_HANDSHAKE_REQUIRED,
                ),
            ),
        )
        val model = WearSurfaceMapper.map(
            WatchSurfaceState(omitted, ActiveFreshness.DISCONNECTED),
        )
        assertEquals(WearSurfaceKind.DISCONNECTED, model.kind)
        assertNull(model.trainingName)
        assertNull(model.exerciseName)
        assertFalse(model.completeEnabled)
    }

    private fun phoneAction(reason: PhoneActionReason): WatchReducerState {
        val snapshot = SnapshotData(
            databaseEpoch = ReducerTestFixtures.epoch,
            payload = SnapshotPayload.PhoneActionRequired(
                sessionUuid = ReducerTestFixtures.sessionA,
                sessionRevision = 3,
                reason = reason,
            ),
        )
        return WatchReducerState(display = WatchDisplayState.PhoneActionRequired(snapshot))
    }

    private fun workoutComplete(): WatchReducerState {
        val snapshot = SnapshotData(
            databaseEpoch = ReducerTestFixtures.epoch,
            payload = SnapshotPayload.WorkoutComplete(
                sessionUuid = ReducerTestFixtures.sessionA,
                sessionRevision = 8,
                trainingName = BoundedDisplayName.Value("Done"),
                completedExercises = 4,
                totalExercises = 4,
            ),
        )
        return WatchReducerState(display = WatchDisplayState.WorkoutComplete(snapshot))
    }

    private fun WatchSurfaceState(
        snapshot: SnapshotData,
        freshness: ActiveFreshness,
    ): WatchReducerState = WatchReducerState(
        display = WatchDisplayState.Active(snapshot, freshness),
    )
}
