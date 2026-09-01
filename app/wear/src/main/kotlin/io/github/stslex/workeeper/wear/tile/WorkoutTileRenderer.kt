// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.tile

import android.content.Context
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.TileBuilders
import io.github.stslex.workeeper.wear.MainActivity
import io.github.stslex.workeeper.wear.R
import io.github.stslex.workeeper.wear.ui.WearCopy
import io.github.stslex.workeeper.wear.ui.WearSurfaceKind
import io.github.stslex.workeeper.wear.ui.WearSurfaceModel
import io.github.stslex.workeeper.wear.ui.statusCopy

internal class WorkoutTileRenderer(private val context: Context) {

    fun render(model: WearSurfaceModel): TileBuilders.Tile {
        val layout = WorkoutTileLayout.build(
            packageName = context.packageName,
            activityClassName = MainActivity::class.java.name,
            lines = lines(model),
        )
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCE_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(layout)
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    private fun lines(model: WearSurfaceModel): List<String> = when (model.kind) {
        WearSurfaceKind.LOADING -> listOf(
            context.getString(R.string.app_name),
            copy(model.statusCopy()),
        )
        WearSurfaceKind.NO_SESSION -> listOf(
            context.getString(R.string.app_name),
            copy(model.statusCopy()),
        )
        WearSurfaceKind.ACTIVE -> activeLines(model)
        WearSurfaceKind.PHONE_ACTION_NO_SETS,
        WearSurfaceKind.PHONE_ACTION_UNSUPPORTED,
        -> listOf(
            model.exerciseName ?: context.getString(R.string.exercise_generic),
            copy(model.statusCopy()),
        )
        WearSurfaceKind.PAYLOAD_TOO_LARGE -> listOf(
            context.getString(R.string.workout_generic),
            copy(model.statusCopy()),
        )
        WearSurfaceKind.WORKOUT_COMPLETE -> listOf(
            model.trainingName ?: context.getString(R.string.workout_generic),
            copy(WearCopy.WORKOUT_COMPLETE),
            copy(WearCopy.FINISH_ON_PHONE),
        )
        WearSurfaceKind.REFRESH_REQUIRED,
        WearSurfaceKind.DISCONNECTED,
        -> listOfNotNull(
            model.trainingName ?: context.getString(R.string.workout_generic),
            model.exerciseName,
            copy(model.statusCopy()),
        )
        WearSurfaceKind.RETRYABLE_ERROR,
        WearSurfaceKind.PROTOCOL_MISMATCH,
        -> listOf(
            context.getString(R.string.app_name),
            copy(model.statusCopy()),
        )
    }

    private fun activeLines(model: WearSurfaceModel): List<String> = listOf(
        model.trainingName ?: context.getString(R.string.workout_generic),
        model.exerciseName ?: context.getString(R.string.exercise_generic),
        context.getString(R.string.set_progress, model.setOrdinal, model.totalSets),
        context.resources.getQuantityString(
            R.plurals.exercise_progress,
            requireNotNull(model.totalExercises),
            requireNotNull(model.completedExercises),
            requireNotNull(model.totalExercises),
        ),
    )

    private fun copy(copy: WearCopy): String = context.getString(copy.resource)

    private companion object {
        const val RESOURCE_VERSION = "wear_phase_1_v1"
    }
}
