// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.mvi.model.DeleteExerciseCopyUiModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DeleteExerciseCopyMapperTest {

    @Test
    fun `ad-hoc workout copy does not claim a plan changes after restoration`() {
        val copy = DeleteExerciseCopyMapper.map(
            isAdhocSession = true,
            isMidSessionAdded = false,
        )

        assertEquals(DeleteExerciseCopyUiModel.ADHOC_WORKOUT, copy)
        assertEquals(R.string.feature_live_workout_delete_workout_title, copy.titleRes)
        assertEquals(R.string.feature_live_workout_delete_workout_body, copy.bodyRes)
        assertEquals(R.string.feature_live_workout_delete_workout_confirm, copy.confirmRes)
        assertEquals(
            R.string.feature_live_workout_toast_exercise_removed_from_workout,
            copy.toastRes,
        )
    }

    @Test
    fun `ad-hoc workout context wins while the added marker is present`() {
        assertEquals(
            DeleteExerciseCopyUiModel.ADHOC_WORKOUT,
            DeleteExerciseCopyMapper.map(
                isAdhocSession = true,
                isMidSessionAdded = true,
            ),
        )
    }

    @Test
    fun `template planned exercise retains plan removal copy`() {
        val copy = DeleteExerciseCopyMapper.map(
            isAdhocSession = false,
            isMidSessionAdded = false,
        )

        assertEquals(DeleteExerciseCopyUiModel.TEMPLATE_PLANNED, copy)
        assertEquals(R.string.feature_live_workout_delete_plan_title, copy.titleRes)
        assertEquals(R.string.feature_live_workout_delete_plan_body_planned, copy.bodyRes)
        assertEquals(R.string.feature_live_workout_delete_plan_confirm, copy.confirmRes)
        assertEquals(R.string.feature_live_workout_toast_exercise_removed, copy.toastRes)
    }

    @Test
    fun `template mid-session addition retains its concise loss body`() {
        val copy = DeleteExerciseCopyMapper.map(
            isAdhocSession = false,
            isMidSessionAdded = true,
        )

        assertEquals(DeleteExerciseCopyUiModel.TEMPLATE_ADDITION, copy)
        assertEquals(R.string.feature_live_workout_delete_plan_title, copy.titleRes)
        assertEquals(R.string.feature_live_workout_delete_plan_body_added, copy.bodyRes)
        assertEquals(R.string.feature_live_workout_delete_plan_confirm, copy.confirmRes)
        assertEquals(R.string.feature_live_workout_toast_exercise_removed, copy.toastRes)
    }
}
