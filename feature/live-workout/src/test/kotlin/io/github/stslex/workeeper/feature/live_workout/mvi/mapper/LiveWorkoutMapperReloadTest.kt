// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.feature.live_workout.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.LiveExerciseDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PerformedExerciseDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SetTypeDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toUiList
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Locks the load path's position propagation: a `SetDomain` stored at position N
 * surfaces in the UI at the same position, regardless of how many other
 * positions are populated. Without that, sparse-position state (e.g. only the
 * 5th set of a 5-set plan marked done) collapses into the first row on reload
 * because the mapper rebuilds positions from the input list index.
 *
 * Cases 4 and 5 also lock the load-path "exercise is done" rule for adhoc:
 * performed positions are part of `expectedPositions`, so an adhoc exercise
 * with sparse done-sets and no drafts (drafts are not persisted) is DONE iff
 * every performed-position row is done.
 */
internal class LiveWorkoutMapperReloadTest {

    @Test
    fun `sparse position 4 only - UI keeps position 4`() {
        val plan = (0 until 5).map {
            PlanSetDomain(weight = 100.0, reps = 5, type = SetTypeDomain.WORK)
        }
        val performed = listOf(
            SetDomain(
                uuid = "set-4",
                weight = 100.0,
                reps = 5,
                type = SetTypeDomain.WORK,
                position = 4,
            ),
        )

        val ui = listOf(exercise(plan = plan, performed = performed))
            .toUiList(activeUuids = emptySet())

        assertEquals(1, ui[0].performedSets.size)
        assertEquals(4, ui[0].performedSets.first().position)
    }

    @Test
    fun `sparse positions 1 and 3 - UI keeps both positions`() {
        val plan = (0 until 5).map {
            PlanSetDomain(weight = 100.0, reps = 5, type = SetTypeDomain.WORK)
        }
        val performed = listOf(
            SetDomain(
                uuid = "set-1",
                weight = 100.0,
                reps = 5,
                type = SetTypeDomain.WORK,
                position = 1,
            ),
            SetDomain(
                uuid = "set-3",
                weight = 100.0,
                reps = 5,
                type = SetTypeDomain.WORK,
                position = 3,
            ),
        )

        val ui = listOf(exercise(plan = plan, performed = performed))
            .toUiList(activeUuids = emptySet())

        assertEquals(setOf(1, 3), ui[0].performedSets.map { it.position }.toSet())
    }

    @Test
    fun `all-done dense plan size 5 - every position matches and status is DONE`() {
        val plan = (0 until 5).map {
            PlanSetDomain(weight = 100.0, reps = 5, type = SetTypeDomain.WORK)
        }
        val performed = (0 until 5).map { idx ->
            SetDomain(
                uuid = "set-$idx",
                weight = 100.0,
                reps = 5,
                type = SetTypeDomain.WORK,
                position = idx,
            )
        }

        val ui = listOf(exercise(plan = plan, performed = performed))
            .toUiList(activeUuids = emptySet())

        assertEquals(ExerciseStatusUiModel.DONE, ui[0].status)
        assertEquals((0..4).toList(), ui[0].performedSets.map { it.position })
    }

    @Test
    fun `adhoc partial - empty plan, single done at position 0, status is DONE`() {
        // Reload has no drafts, so expectedPositions = performed positions only.
        // Single performed-and-done at pos 0 → DONE.
        val performed = listOf(
            SetDomain(
                uuid = "set-0",
                weight = null,
                reps = 10,
                type = SetTypeDomain.WORK,
                position = 0,
            ),
        )

        val ui = listOf(exercise(plan = null, performed = performed))
            .toUiList(activeUuids = emptySet())

        assertEquals(ExerciseStatusUiModel.DONE, ui[0].status)
    }

    @Test
    fun `adhoc full sparse - empty plan, performed positions 2 and 4, status is DONE`() {
        // expectedPositions = {2, 4}; both performed-and-done → DONE.
        val performed = listOf(
            SetDomain(
                uuid = "set-2",
                weight = null,
                reps = 10,
                type = SetTypeDomain.WORK,
                position = 2,
            ),
            SetDomain(
                uuid = "set-4",
                weight = null,
                reps = 10,
                type = SetTypeDomain.WORK,
                position = 4,
            ),
        )

        val ui = listOf(exercise(plan = null, performed = performed))
            .toUiList(activeUuids = emptySet())

        assertEquals(ExerciseStatusUiModel.DONE, ui[0].status)
        assertEquals(setOf(2, 4), ui[0].performedSets.map { it.position }.toSet())
    }

    private fun exercise(
        plan: List<PlanSetDomain>?,
        performed: List<SetDomain>,
    ): LiveExerciseDomain = LiveExerciseDomain(
        performed = PerformedExerciseDomain(
            uuid = "pe-1",
            sessionUuid = "session-1",
            exerciseUuid = "exercise-1",
            position = 0,
            skipped = false,
            exerciseName = "Bench Press",
        ),
        exerciseType = ExerciseTypeDomain.WEIGHTED,
        planSets = plan,
        performedSets = performed,
        isPlanAttached = true,
    )
}
