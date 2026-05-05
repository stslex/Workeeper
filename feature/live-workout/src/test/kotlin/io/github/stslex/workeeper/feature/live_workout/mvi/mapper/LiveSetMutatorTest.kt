// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the extracted state mutator. Drives every public method with
 * representative state shapes and asserts the resulting `State` snapshot —
 * `performedSets`, `setDrafts`, `status`, and `visibleSets` — without standing up a
 * handler or a store.
 *
 * `ResourceWrapper` is relaxed-mocked because the mutator only forwards it through to
 * `withPresentation` for label shaping; assertions key off structure, not display
 * strings.
 */
internal class LiveSetMutatorTest {

    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    private val statusMapper = StateStatusMapper(resourceWrapper)
    private val mutator = LiveSetMutator(resourceWrapper, statusMapper)

    @Test
    fun `findExercise returns the matching exercise or null`() {
        val state = stateWith(exerciseWithPlan(plan = persistentListOf()))

        assertEquals(PE_UUID, mutator.findExercise(state, PE_UUID)?.performedExerciseUuid)
        assertNull(mutator.findExercise(state, "missing"))
    }

    @Test
    fun `draftFor returns existing draft when present`() {
        val state = stateWith(exerciseWithPlan(plan = persistentListOf()))
            .copy(
                setDrafts = persistentMapOf(
                    State.DraftKey(PE_UUID, 0) to LiveSetUiModel(
                        position = 0,
                        weight = 87.5,
                        reps = 12,
                        type = SetTypeUiModel.FAILURE,
                        isDone = false,
                    ),
                ),
            )

        val seed = mutator.draftFor(state, PE_UUID, position = 0)

        assertEquals(87.5, seed.weight)
        assertEquals(12, seed.reps)
        assertEquals(SetTypeUiModel.FAILURE, seed.type)
    }

    @Test
    fun `draftFor falls back to performed set when no draft exists`() {
        val state = stateWith(
            exerciseWithPlan(
                plan = persistentListOf(
                    PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
                ),
                performed = persistentListOf(
                    LiveSetUiModel(
                        position = 0,
                        weight = 110.0,
                        reps = 6,
                        type = SetTypeUiModel.WORK,
                        isDone = true,
                    ),
                ),
            ),
        )

        val seed = mutator.draftFor(state, PE_UUID, position = 0)

        assertEquals(110.0, seed.weight)
        assertEquals(6, seed.reps)
        assertEquals(true, seed.isDone)
    }

    @Test
    fun `draftFor falls back to plan when no draft or performed exists`() {
        val state = stateWith(
            exerciseWithPlan(
                plan = persistentListOf(
                    PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WARMUP),
                ),
            ),
        )

        val seed = mutator.draftFor(state, PE_UUID, position = 0)

        assertEquals(100.0, seed.weight)
        assertEquals(5, seed.reps)
        assertEquals(SetTypeUiModel.WARMUP, seed.type)
        assertEquals(false, seed.isDone)
    }

    @Test
    fun `draftFor returns fallback empty row when no source covers the position`() {
        val state = stateWith(exerciseWithPlan(plan = persistentListOf()))

        val seed = mutator.draftFor(state, PE_UUID, position = 0)

        assertNull(seed.weight)
        assertEquals(0, seed.reps)
        assertEquals(SetTypeUiModel.WORK, seed.type)
        assertEquals(false, seed.isDone)
    }

    @Test
    fun `applySetMarked clears the draft and writes a performed set with draft fields`() {
        val draft = LiveSetUiModel(
            position = 0,
            weight = 105.0,
            reps = 6,
            type = SetTypeUiModel.WORK,
            isDone = false,
        )
        val state = stateWith(exerciseWithPlan(plan = persistentListOf()))
            .copy(setDrafts = persistentMapOf(State.DraftKey(PE_UUID, 0) to draft))

        val result = mutator.applySetMarked(state, PE_UUID, position = 0, draft = draft)

        assertNull(result.setDrafts[State.DraftKey(PE_UUID, 0)])
        val performed = result.exercises.first().performedSets.single()
        assertEquals(105.0, performed.weight)
        assertEquals(6, performed.reps)
        assertEquals(true, performed.isDone)
    }

    @Test
    fun `applySetUnchecked drops the performed set at the given position`() {
        val state = stateWith(
            exerciseWithPlan(
                plan = persistentListOf(),
                performed = persistentListOf(
                    LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                ),
            ),
        )

        val result = mutator.applySetUnchecked(state, PE_UUID, position = 0)

        assertTrue(result.exercises.first().performedSets.isEmpty())
    }

    @Test
    fun `applySetTypeChange rewrites the type of the targeted performed set only`() {
        val state = stateWith(
            exerciseWithPlan(
                plan = persistentListOf(),
                performed = persistentListOf(
                    LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                    LiveSetUiModel(1, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                ),
            ),
        )

        val result = mutator.applySetTypeChange(state, PE_UUID, position = 1, type = SetTypeUiModel.FAILURE)

        val performed = result.exercises.first().performedSets
        assertEquals(SetTypeUiModel.WORK, performed[0].type)
        assertEquals(SetTypeUiModel.FAILURE, performed[1].type)
        // Visible row resolver must have re-run so visibleSets reflects performed.
        assertEquals(SetTypeUiModel.FAILURE, result.exercises.first().visibleSets[1].type)
    }

    @Test
    fun `applyResetSets clears performed sets and drafts only for the targeted exercise`() {
        val state = stateWith(
            exerciseWithPlan(
                plan = persistentListOf(),
                performed = persistentListOf(
                    LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                ),
            ),
        ).copy(
            pendingResetExerciseUuid = PE_UUID,
            setDrafts = mapOf(
                State.DraftKey(PE_UUID, 1) to LiveSetUiModel(1, 110.0, 5, SetTypeUiModel.WORK, isDone = false),
                State.DraftKey("OTHER", 0) to LiveSetUiModel(0, 50.0, 3, SetTypeUiModel.WARMUP, isDone = false),
            ).toImmutableMap(),
        )

        val result = mutator.applyResetSets(state, PE_UUID)

        assertTrue(result.exercises.first().performedSets.isEmpty())
        assertNull(result.setDrafts[State.DraftKey(PE_UUID, 1)])
        assertNotNull(result.setDrafts[State.DraftKey("OTHER", 0)])
        assertNull(result.pendingResetExerciseUuid)
    }

    @Test
    fun `applySkip clears drafts and flips status to SKIPPED`() {
        val state = stateWith(
            exerciseWithPlan(
                plan = persistentListOf(
                    PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
                ),
            ),
        ).copy(
            pendingSkipExerciseUuid = PE_UUID,
            setDrafts = persistentMapOf(
                State.DraftKey(PE_UUID, 0) to LiveSetUiModel(0, 110.0, 5, SetTypeUiModel.WORK, isDone = false),
            ),
        )

        val result = mutator.applySkip(state, PE_UUID)

        assertEquals(ExerciseStatusUiModel.SKIPPED, result.exercises.first().status)
        assertTrue(result.setDrafts.isEmpty())
        assertNull(result.pendingSkipExerciseUuid)
    }

    @Test
    fun `applyAddSet seeds the next position from the last plan row`() {
        val state = stateWith(
            exerciseWithPlan(
                plan = persistentListOf(
                    PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
                    PlanSetUiModel(weight = 102.5, reps = 5, type = SetTypeUiModel.WORK),
                ),
            ),
        )

        val result = mutator.applyAddSet(state, PE_UUID)

        val draft = result.setDrafts[State.DraftKey(PE_UUID, 2)]
        assertNotNull(draft)
        assertEquals(2, draft?.position)
        assertEquals(102.5, draft?.weight)
        assertEquals(5, draft?.reps)
        assertEquals(SetTypeUiModel.WORK, draft?.type)
        assertEquals(false, draft?.isDone)
    }

    @Test
    fun `applyAddSet returns the state unchanged for an unknown exercise uuid`() {
        val state = stateWith(exerciseWithPlan(plan = persistentListOf()))

        val result = mutator.applyAddSet(state, "unknown")

        assertEquals(state, result)
    }

    @Test
    fun `nextSetPosition is one past the highest of plan, performed, and draft positions`() {
        val state = stateWith(
            exerciseWithPlan(
                plan = persistentListOf(
                    PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
                    PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
                ),
                performed = persistentListOf(
                    LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                ),
            ),
        ).copy(
            setDrafts = persistentMapOf(
                State.DraftKey(PE_UUID, 4) to LiveSetUiModel(4, 110.0, 5, SetTypeUiModel.WORK, isDone = false),
            ),
        )
        val exercise = state.exercises.first()

        assertEquals(5, mutator.nextSetPosition(state, exercise))
    }

    @Test
    fun `lastKnownSetSeed prefers the latest draft over plan and performed`() {
        val state = stateWith(
            exerciseWithPlan(
                plan = persistentListOf(
                    PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
                ),
                performed = persistentListOf(
                    LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                ),
            ),
        ).copy(
            setDrafts = persistentMapOf(
                State.DraftKey(PE_UUID, 3) to LiveSetUiModel(3, 88.0, 11, SetTypeUiModel.DROP, isDone = false),
            ),
        )
        val exercise = state.exercises.first()

        val seed = mutator.lastKnownSetSeed(state, exercise)

        assertEquals(88.0, seed?.weight)
        assertEquals(11, seed?.reps)
        assertEquals(SetTypeUiModel.DROP, seed?.type)
    }

    @Test
    fun `lastKnownSetSeed returns null when no source has any rows`() {
        val state = stateWith(exerciseWithPlan(plan = persistentListOf()))
        val exercise = state.exercises.first()

        assertNull(mutator.lastKnownSetSeed(state, exercise))
    }

    @Test
    fun `recomputeStatuses delegates to the status mapper and recomputes presentation`() {
        // Simulate a stale `status` field — recomputeStatuses should re-derive it.
        val state = stateWith(
            exerciseWithPlan(
                plan = persistentListOf(
                    PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
                ),
                performed = persistentListOf(
                    LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
                ),
                status = ExerciseStatusUiModel.PENDING, // stale
            ),
        )

        val result = mutator.recomputeStatuses(state)

        assertEquals(ExerciseStatusUiModel.DONE, result.exercises.first().status)
    }

    private fun stateWith(exercise: LiveExerciseUiModel): State = State.create(
        sessionUuid = "session-1",
        trainingUuid = "training-1",
    ).copy(
        isLoading = false,
        exercises = persistentListOf(exercise),
    )

    private fun exerciseWithPlan(
        plan: kotlinx.collections.immutable.ImmutableList<PlanSetUiModel>,
        performed: kotlinx.collections.immutable.ImmutableList<LiveSetUiModel> = persistentListOf(),
        status: ExerciseStatusUiModel = ExerciseStatusUiModel.CURRENT,
    ): LiveExerciseUiModel = LiveExerciseUiModel(
        performedExerciseUuid = PE_UUID,
        exerciseUuid = "ex-1",
        exerciseName = "Bench Press",
        exerciseType = ExerciseTypeUiModel.WEIGHTED,
        position = 0,
        status = status,
        statusLabel = "",
        planSets = plan,
        performedSets = performed,
    )

    private companion object {
        const val PE_UUID = "pe-1"
    }
}
