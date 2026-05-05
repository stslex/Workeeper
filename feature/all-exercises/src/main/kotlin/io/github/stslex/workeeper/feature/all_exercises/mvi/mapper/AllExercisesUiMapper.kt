// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.all_exercises.R
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseListItemDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.TagDomain
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.TagUiModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

internal object AllExercisesUiMapper {

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60 * MINUTE_MS
    private const val DAY_MS = 24 * HOUR_MS

    fun ExerciseListItemDomain.toUi(
        resourceWrapper: ResourceWrapper,
        nowMillis: Long = System.currentTimeMillis(),
    ): ExerciseUiModel = ExerciseUiModel(
        uuid = exercise.uuid,
        name = exercise.name,
        type = exercise.type.toUi(),
        tags = if (tags.isEmpty()) persistentListOf() else tags.toImmutableList(),
        sessionCount = sessionCount,
        linkedTrainingsCount = linkedTrainingsCount,
        lastTrainedAt = lastTrainedAt,
        footerLabel = composeFooterLabel(
            resourceWrapper = resourceWrapper,
            sessionCount = sessionCount,
            linkedTrainingsCount = linkedTrainingsCount,
            lastTrainedAt = lastTrainedAt,
            nowMillis = nowMillis,
        ),
        imagePath = exercise.imagePath,
    )

    fun composeFooterLabel(
        resourceWrapper: ResourceWrapper,
        sessionCount: Int,
        linkedTrainingsCount: Int,
        lastTrainedAt: Long?,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val separator = " ${resourceWrapper.getString(R.string.feature_all_exercises_footer_separator)} "
        val segments = listOfNotNull(
            sessionCount.takeIf { it > 0 }?.let { count ->
                resourceWrapper.getQuantityString(
                    R.plurals.feature_all_exercises_session_count,
                    count,
                    count,
                )
            },
            linkedTrainingsCount.takeIf { it > 0 }?.let { count ->
                resourceWrapper.getQuantityString(
                    R.plurals.feature_all_exercises_linked_trainings_count,
                    count,
                    count,
                )
            },
            lastTrainedAt?.let { lastTrainedRelativeLabel(resourceWrapper, it, nowMillis) },
        )
        return segments.joinToString(separator)
    }

    private fun lastTrainedRelativeLabel(
        resourceWrapper: ResourceWrapper,
        lastTrainedAt: Long,
        nowMillis: Long,
    ): String {
        val deltaMs = (nowMillis - lastTrainedAt).coerceAtLeast(0L)
        return when {
            deltaMs < MINUTE_MS ->
                resourceWrapper.getString(R.string.feature_all_exercises_last_trained_just_now)

            deltaMs < HOUR_MS -> {
                val minutes = (deltaMs / MINUTE_MS).toInt()
                resourceWrapper.getQuantityString(
                    R.plurals.feature_all_exercises_last_trained_minutes,
                    minutes,
                    minutes,
                )
            }

            deltaMs < DAY_MS -> {
                val hours = (deltaMs / HOUR_MS).toInt()
                resourceWrapper.getQuantityString(
                    R.plurals.feature_all_exercises_last_trained_hours,
                    hours,
                    hours,
                )
            }

            else -> {
                val days = (deltaMs / DAY_MS).toInt()
                resourceWrapper.getQuantityString(
                    R.plurals.feature_all_exercises_last_trained_days,
                    days,
                    days,
                )
            }
        }
    }

    fun ExerciseTypeDomain.toUi(): ExerciseTypeUiModel = when (this) {
        ExerciseTypeDomain.WEIGHTED -> ExerciseTypeUiModel.WEIGHTED
        ExerciseTypeDomain.WEIGHTLESS -> ExerciseTypeUiModel.WEIGHTLESS
    }

    fun TagDomain.toUi(): TagUiModel = TagUiModel(uuid = uuid, name = name)
}
