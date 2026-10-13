// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import io.github.stslex.workeeper.core.wear.protocol.BoundedDisplayName
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.MutationUnavailableReason
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.OmissionReason
import io.github.stslex.workeeper.core.wear.protocol.OutlineExercise
import io.github.stslex.workeeper.core.wear.protocol.OutlineSet
import io.github.stslex.workeeper.core.wear.protocol.ProtocolRejectionReason
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
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
            noSets(BoundedDisplayName.Value("Rows missing")) to WearSurfaceKind.PHONE_ACTION_NO_SETS,
            blocked(NumericField.WEIGHT, BoundedDisplayName.Value("Unsupported")) to
                WearSurfaceKind.PHONE_ACTION_UNSUPPORTED,
            payloadTooLarge() to WearSurfaceKind.PAYLOAD_TOO_LARGE,
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
        val payload = source.payload as SnapshotPayload.Active
        val omitted = source.copy(
            payload = payload.copy(
                trainingName = BoundedDisplayName.Omitted(OmissionReason.TOO_LARGE),
                exercises = payload.exercises.mapIndexed { index, exercise ->
                    val name = BoundedDisplayName.Omitted(OmissionReason.INVALID_UNICODE)
                    if (index == 0) exercise.copy(name = name) else exercise
                },
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

    @Test
    fun `phone action reasons preserve bounded names and omit unsafe names`() {
        val names = listOf(
            BoundedDisplayName.Value("Current exercise") to "Current exercise",
            BoundedDisplayName.Omitted(OmissionReason.TOO_LARGE) to null,
            BoundedDisplayName.Omitted(OmissionReason.INVALID_UNICODE) to null,
        )

        names.forEach { (name, expectedName) ->
            val noRows = WearSurfaceMapper.map(noSets(name))
            assertPhoneAction(noRows, WearSurfaceKind.PHONE_ACTION_NO_SETS, expectedName, null)

            listOf(NumericField.REPS, NumericField.WEIGHT).forEach { field ->
                val unsupported = WearSurfaceMapper.map(blocked(field, name))
                assertPhoneAction(
                    unsupported,
                    WearSurfaceKind.PHONE_ACTION_UNSUPPORTED,
                    expectedName,
                    field,
                )
            }
        }
    }

    @Test
    fun `completed workout omits unsafe training name and remains targetless`() {
        listOf(OmissionReason.TOO_LARGE, OmissionReason.INVALID_UNICODE).forEach { reason ->
            val model = WearSurfaceMapper.map(
                workoutComplete(BoundedDisplayName.Omitted(reason)),
            )

            assertEquals(WearSurfaceKind.WORKOUT_COMPLETE, model.kind)
            assertNull(model.trainingName)
            assertNull(model.exerciseName)
            assertNull(model.setOrdinal)
            assertNull(model.reps)
            assertFalse(model.controlsVisible)
            assertFalse(model.completeEnabled)
        }
    }

    private fun assertPhoneAction(
        model: WearSurfaceModel,
        expectedKind: WearSurfaceKind,
        expectedName: String?,
        expectedField: NumericField?,
    ) {
        assertEquals(expectedKind, model.kind)
        assertEquals(expectedName, model.exerciseName)
        assertEquals(expectedField, model.fieldError)
        assertNull(model.setOrdinal)
        assertNull(model.reps)
        assertNull(model.weightHundredthsKg)
        assertFalse(model.controlsVisible)
        assertFalse(model.completeEnabled)
    }

    /** Rule R stops at an exercise without sets (P12). */
    private fun noSets(name: BoundedDisplayName): WatchReducerState = outline(listOf(exercise(name, emptyList())))

    /** Rule R stops at a set the watch cannot show (P8). */
    private fun blocked(field: NumericField, name: BoundedDisplayName): WatchReducerState =
        outline(listOf(exercise(name, listOf(OutlineSet.Blocked(0, SetTypeWire.WORK, field)))))

    private fun payloadTooLarge(): WatchReducerState = WatchReducerState(
        display = WatchDisplayState.PayloadTooLarge(
            SnapshotData(
                ReducerTestFixtures.epoch,
                SnapshotPayload.PayloadTooLarge(ReducerTestFixtures.sessionA, 3),
            ),
        ),
    )

    /** Every exercise finished or skipped: rule R selects nothing (§5.3). */
    private fun workoutComplete(
        trainingName: BoundedDisplayName = BoundedDisplayName.Value("Done"),
    ): WatchReducerState = outline(
        exercises = listOf(
            exercise(BoundedDisplayName.Value("Squat"), listOf(ReducerTestFixtures.done(0))),
            exercise(
                BoundedDisplayName.Value("Plank"),
                listOf(ReducerTestFixtures.open(0)),
                skipped = true,
                uuid = ReducerTestFixtures.exerciseB,
            ),
        ),
        trainingName = trainingName,
    )

    private fun outline(
        exercises: List<OutlineExercise>,
        trainingName: BoundedDisplayName = BoundedDisplayName.Value("Training"),
    ): WatchReducerState {
        val source = ReducerTestFixtures.active()
        val payload = (source.payload as SnapshotPayload.Active)
            .copy(trainingName = trainingName, exercises = exercises)
        return WatchSurfaceState(source.copy(payload = payload), ActiveFreshness.FRESH)
    }

    private fun exercise(
        name: BoundedDisplayName,
        sets: List<OutlineSet>,
        skipped: Boolean = false,
        uuid: CanonicalUuid = ReducerTestFixtures.exerciseA,
    ) = ReducerTestFixtures.outlineExercise(uuid, sets, skipped).copy(name = name)

    private fun WatchSurfaceState(
        snapshot: SnapshotData,
        freshness: ActiveFreshness,
    ): WatchReducerState = WatchReducerState(
        display = WatchDisplayState.Active(snapshot, freshness),
    )
}
