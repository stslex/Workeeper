// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Locks the visible-row resolution priority: **performed > draft > plan > fallback**.
 * Targets the production resolver in `LiveSetRowsResolver` directly.
 */
internal class LiveSetVisibleRowsResolverTest {

    @Test
    fun `performed row wins over draft and plan at the same position`() {
        val exercise = exercise(
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
        )
        val drafts = draftMap(0 to LiveSetUiModel(0, 88.0, 8, SetTypeUiModel.FAILURE, isDone = false))

        val rows = resolveVisibleRows(exercise, drafts)

        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(110.0, row.weight)
        assertEquals(6, row.reps)
        assertEquals(SetTypeUiModel.WORK, row.type)
        assertEquals(true, row.isDone)
    }

    @Test
    fun `draft wins over plan when no performed row exists`() {
        val exercise = exercise(
            plan = persistentListOf(
                PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
            ),
            performed = persistentListOf(),
        )
        val drafts = draftMap(0 to LiveSetUiModel(0, 88.0, 8, SetTypeUiModel.FAILURE, isDone = false))

        val rows = resolveVisibleRows(exercise, drafts)

        val row = rows.single()
        assertEquals(88.0, row.weight)
        assertEquals(8, row.reps)
        assertEquals(SetTypeUiModel.FAILURE, row.type)
        assertEquals(false, row.isDone)
    }

    @Test
    fun `plan is used when no performed or draft exists at the position`() {
        val exercise = exercise(
            plan = persistentListOf(
                PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
            ),
            performed = persistentListOf(),
        )

        val rows = resolveVisibleRows(exercise, persistentMapOf())

        val row = rows.single()
        assertEquals(100.0, row.weight)
        assertEquals(5, row.reps)
        assertEquals(SetTypeUiModel.WORK, row.type)
        assertEquals(false, row.isDone)
    }

    @Test
    fun `fallback empty row is used when no source covers the requested position`() {
        // Plan size = 1, performed empty, draft at position 1 (beyond plan). Position 0
        // has nothing → fallback.
        val exercise = exercise(
            plan = persistentListOf(
                PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
            ),
            performed = persistentListOf(),
        )
        val drafts = draftMap(1 to LiveSetUiModel(1, 50.0, 3, SetTypeUiModel.WARMUP, isDone = false))

        val rows = resolveVisibleRows(exercise, drafts)

        // Two rows: position 0 from plan, position 1 from draft.
        assertEquals(2, rows.size)
        assertEquals(100.0, rows[0].weight)
        assertEquals(50.0, rows[1].weight)
        assertEquals(3, rows[1].reps)
        assertEquals(SetTypeUiModel.WARMUP, rows[1].type)
    }

    @Test
    fun `fallback used when neither plan nor performed nor draft covers a position`() {
        // Empty plan, empty performed, draft only at position 2 → positions 0 and 1
        // resolve to fallback rows.
        val exercise = exercise(plan = persistentListOf(), performed = persistentListOf())
        val drafts = draftMap(2 to LiveSetUiModel(2, 60.0, 4, SetTypeUiModel.DROP, isDone = false))

        val rows = resolveVisibleRows(exercise, drafts)

        assertEquals(3, rows.size)
        listOf(rows[0], rows[1]).forEach { row ->
            assertEquals(null, row.weight)
            assertEquals(0, row.reps)
            assertEquals(SetTypeUiModel.WORK, row.type)
            assertEquals(false, row.isDone)
        }
        assertEquals(60.0, rows[2].weight)
    }

    @Test
    fun `drafts beyond plan and performed sizes are included as additional rows`() {
        val exercise = exercise(
            plan = persistentListOf(
                PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
            ),
            performed = persistentListOf(
                LiveSetUiModel(
                    position = 0,
                    weight = 100.0,
                    reps = 5,
                    type = SetTypeUiModel.WORK,
                    isDone = true,
                ),
            ),
        )
        val drafts = draftMap(
            1 to LiveSetUiModel(1, 102.5, 5, SetTypeUiModel.WORK, isDone = false),
            2 to LiveSetUiModel(2, 105.0, 4, SetTypeUiModel.WORK, isDone = false),
        )

        val rows = resolveVisibleRows(exercise, drafts)

        assertEquals(3, rows.size)
        assertEquals(true, rows[0].isDone) // performed
        assertEquals(102.5, rows[1].weight)
        assertEquals(105.0, rows[2].weight)
    }

    @Test
    fun `drafts for other exercises do not leak into the resolved row list`() {
        val exercise = exercise(
            plan = persistentListOf(
                PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
            ),
            performed = persistentListOf(),
        )
        val drafts = persistentMapOf(
            State.DraftKey("OTHER", 0) to LiveSetUiModel(0, 999.0, 99, SetTypeUiModel.FAILURE, false),
        )

        val rows = resolveVisibleRows(exercise, drafts)

        assertEquals(1, rows.size)
        assertEquals(100.0, rows.single().weight) // plan, not the foreign draft
    }

    @Test
    fun `performed row beyond performed list size is included by position`() {
        val exercise = exercise(
            plan = persistentListOf(),
            performed = persistentListOf(
                LiveSetUiModel(
                    position = 2,
                    weight = 120.0,
                    reps = 3,
                    type = SetTypeUiModel.WORK,
                    isDone = true,
                ),
            ),
        )

        val rows = LiveSetRowsResolver.resolveVisibleSets(exercise, persistentMapOf())

        assertEquals(3, rows.size)
        assertEquals("position 0 is an empty placeholder before the sparse performed row", null, rows[0].weight)
        assertEquals("position 1 is an empty placeholder before the sparse performed row", null, rows[1].weight)
        assertEquals(120.0, rows[2].weight)
        assertEquals(true, rows[2].isDone)
    }

    private fun exercise(
        plan: kotlinx.collections.immutable.ImmutableList<PlanSetUiModel>,
        performed: kotlinx.collections.immutable.ImmutableList<LiveSetUiModel>,
    ): LiveExerciseUiModel = LiveExerciseUiModel(
        performedExerciseUuid = PE_UUID,
        exerciseUuid = "ex-1",
        exerciseName = "Bench Press",
        exerciseType = ExerciseTypeUiModel.WEIGHTED,
        position = 0,
        status = ExerciseStatusUiModel.CURRENT,
        statusLabel = "",
        planSets = plan,
        performedSets = performed,
    )

    private fun draftMap(
        vararg entries: Pair<Int, LiveSetUiModel>,
    ): ImmutableMap<State.DraftKey, LiveSetUiModel> = entries
        .associate { (position, set) -> State.DraftKey(PE_UUID, position) to set }
        .toImmutableMap()

    private fun resolveVisibleRows(
        exercise: LiveExerciseUiModel,
        drafts: ImmutableMap<State.DraftKey, LiveSetUiModel>,
    ): List<LiveSetUiModel> = LiveSetRowsResolver.resolveVisibleSets(exercise, drafts)

    private companion object {
        const val PE_UUID = "pe-1"
    }
}
