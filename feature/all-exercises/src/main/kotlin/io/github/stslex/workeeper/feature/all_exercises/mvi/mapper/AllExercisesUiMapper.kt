// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.all_exercises.R
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.TagDomain
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.TagUiModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

internal fun ExerciseDomain.toUi(
    resourceWrapper: ResourceWrapper,
    sessionCount: Int = 0,
    linkedTrainingsCount: Int = 0,
    lastTrainedAt: Long? = null,
    tags: List<String> = emptyList(),
    nowMillis: Long = System.currentTimeMillis(),
): ExerciseUiModel = ExerciseUiModel(
    uuid = uuid,
    name = name,
    type = type.toUi(),
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
    imagePath = imagePath,
)

/**
 * Composes "12 sessions · in 3 trainings · last 4d ago" with hidden segments when
 * `sessionCount == 0`, `linkedTrainingsCount == 0`, or `lastTrainedAt == null`. Returns an
 * empty string when all three are absent so callers can decide whether to render the
 * row's footer slot at all.
 */
internal fun composeFooterLabel(
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

internal fun ExerciseTypeDomain.toUi(): ExerciseTypeUiModel = when (this) {
    ExerciseTypeDomain.WEIGHTED -> ExerciseTypeUiModel.WEIGHTED
    ExerciseTypeDomain.WEIGHTLESS -> ExerciseTypeUiModel.WEIGHTLESS
}

internal fun TagDomain.toUi(): TagUiModel = TagUiModel(uuid = uuid, name = name)
