// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.tile

import io.github.stslex.workeeper.core.wear.protocol.BoundedDisplayName
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.OutlineExercise
import io.github.stslex.workeeper.core.wear.protocol.OutlineRules
import io.github.stslex.workeeper.core.wear.protocol.OutlineSet
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.wear.R
import io.github.stslex.workeeper.wear.runtime.WatchRuntimeSnapshot
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import io.github.stslex.workeeper.wear.state.WatchWorkoutReducer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/** wear-phase-2-exercise-list.md §7.6: the Tile lines of each kind of selection. */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class WorkoutTileOutlineLinesTest {

    // The Robolectric application exists only once a test runs, never at construction.
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `a selected open set shows training, exercise, set progress and exercise progress`() {
        val lines = lines(
            exercise("Squat", listOf(ReducerTestFixtures.done(0), ReducerTestFixtures.open(1))),
            exercise("Plank", listOf(ReducerTestFixtures.done(0)), uuid = ReducerTestFixtures.exerciseB),
            exercise("Row", listOf(ReducerTestFixtures.open(0)), uuid = ReducerTestFixtures.laterExercise),
        )

        assertEquals(
            listOf(
                TRAINING,
                "Squat",
                context.getString(R.string.set_progress, 2, 2),
                context.resources.getQuantityString(R.plurals.exercise_progress, 3, 1, 3),
            ),
            lines,
        )
    }

    @Test
    fun `a selected blocked set asks to edit the set on the phone`() {
        val lines = lines(
            exercise("Squat", listOf(OutlineSet.Blocked(0, SetTypeWire.WORK, NumericField.WEIGHT))),
        )

        assertEquals(listOf(TRAINING, "Squat", context.getString(R.string.edit_set_on_phone)), lines)
    }

    @Test
    fun `a selection without a position asks to add a set on the phone`() {
        val lines = lines(exercise("Squat", emptyList()))

        assertEquals(listOf(TRAINING, "Squat", context.getString(R.string.add_set_on_phone)), lines)
    }

    @Test
    fun `no selection shows the workout-complete lines`() {
        val lines = lines(
            exercise("Squat", listOf(ReducerTestFixtures.done(0))),
            exercise("Plank", emptyList(), skipped = true, uuid = ReducerTestFixtures.exerciseB),
        )

        assertEquals(
            listOf(
                TRAINING,
                context.getString(R.string.workout_complete),
                context.getString(R.string.finish_on_phone),
            ),
            lines,
        )
    }

    /** The Tile lines of an admitted outline whose selection is rule R's, as the owner publishes it. */
    private fun lines(vararg exercises: OutlineExercise): List<String> {
        val source = ReducerTestFixtures.active()
        val payload = (source.payload as SnapshotPayload.Active).copy(
            trainingName = BoundedDisplayName.Value(TRAINING),
            exercises = exercises.toList(),
        )
        val reducer = WatchWorkoutReducer()
        val correlation = ReducerTestFixtures.id(1)
        reducer.issueHandshake(correlation, 0)
        assertTrue(reducer.receiveSnapshot(correlation, source.copy(payload = payload), 0).accepted)
        val snapshot = WatchRuntimeSnapshot(workout = reducer.state, selection = OutlineRules.defaultSelection(payload))
        return WorkoutTileRenderer(context).refreshKey(snapshot).lines
    }

    private fun exercise(
        name: String,
        sets: List<OutlineSet>,
        skipped: Boolean = false,
        uuid: CanonicalUuid = ReducerTestFixtures.exerciseA,
    ) = ReducerTestFixtures.outlineExercise(uuid, sets, skipped).copy(name = BoundedDisplayName.Value(name))

    private companion object {
        const val TRAINING = "Leg day"
    }
}
