// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.DeleteExerciseCopyMapper.bodyRes
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.DeleteExerciseCopyMapper.confirmRes
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.DeleteExerciseCopyMapper.titleRes
import io.github.stslex.workeeper.feature.live_workout.mvi.model.DeleteExerciseCopyUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.NAMED_COPY
import io.github.stslex.workeeper.feature.live_workout.resources.Res
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_plan_body_added
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_plan_body_planned
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_plan_confirm
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_plan_title
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_workout_body
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_workout_confirm
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_workout_title
import kotlin.test.Test
import kotlin.test.assertEquals

internal class DeleteExerciseCopyMapperTest {

    @Test
    fun `ad-hoc workout copy does not claim a plan changes after restoration`() {
        val copy = DeleteExerciseCopyMapper.map(
            isAdhocSession = true,
            isMidSessionAdded = false,
        )

        assertEquals(DeleteExerciseCopyUiModel.ADHOC_WORKOUT, copy)
        assertEquals(Res.string.feature_live_workout_delete_workout_title, copy.titleRes())
        assertEquals(Res.string.feature_live_workout_delete_workout_body, copy.bodyRes())
        assertEquals(Res.string.feature_live_workout_delete_workout_confirm, copy.confirmRes())
        assertEquals(
            "toastExerciseRemovedFromWorkoutTemplate %1\$s",
            DeleteExerciseCopyMapper.toastTemplate(copy, NAMED_COPY),
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
        assertEquals(Res.string.feature_live_workout_delete_plan_title, copy.titleRes())
        assertEquals(Res.string.feature_live_workout_delete_plan_body_planned, copy.bodyRes())
        assertEquals(Res.string.feature_live_workout_delete_plan_confirm, copy.confirmRes())
        assertEquals("toastExerciseRemovedTemplate %1\$s", DeleteExerciseCopyMapper.toastTemplate(copy, NAMED_COPY))
    }

    @Test
    fun `template mid-session addition retains its concise loss body`() {
        val copy = DeleteExerciseCopyMapper.map(
            isAdhocSession = false,
            isMidSessionAdded = true,
        )

        assertEquals(DeleteExerciseCopyUiModel.TEMPLATE_ADDITION, copy)
        assertEquals(Res.string.feature_live_workout_delete_plan_title, copy.titleRes())
        assertEquals(Res.string.feature_live_workout_delete_plan_body_added, copy.bodyRes())
        assertEquals(Res.string.feature_live_workout_delete_plan_confirm, copy.confirmRes())
        assertEquals("toastExerciseRemovedTemplate %1\$s", DeleteExerciseCopyMapper.toastTemplate(copy, NAMED_COPY))
    }
}
