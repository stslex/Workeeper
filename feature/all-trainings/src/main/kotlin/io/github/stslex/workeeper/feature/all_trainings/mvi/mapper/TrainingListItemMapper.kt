// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.exercise.training.TrainingListItem
import io.github.stslex.workeeper.feature.all_trainings.R
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingListItemUi
import kotlinx.collections.immutable.toImmutableList

object TrainingListItemMapper {

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60 * MINUTE_MS
    private const val DAY_MS = 24 * HOUR_MS

    internal fun TrainingListItem.toUi(
        resourceWrapper: ResourceWrapper,
        nowMillis: Long = System.currentTimeMillis(),
    ): TrainingListItemUi = TrainingListItemUi(
        uuid = data.uuid,
        name = data.name,
        tags = data.labels.toImmutableList(),
        exerciseCount = exerciseCount,
        isActive = isActive,
        statusLabel = statusLabel(resourceWrapper, nowMillis),
    )

    private fun TrainingListItem.statusLabel(
        resourceWrapper: ResourceWrapper,
        nowMillis: Long,
    ): String {
        val sessionStartedAt = activeSessionStartedAt
        val lastSessionAt = lastSessionAt
        return when {
            isActive && sessionStartedAt != null ->
                resourceWrapper.getString(
                    R.string.feature_all_trainings_status_in_progress_format,
                    sessionStartedAt.relativeAgo(resourceWrapper, nowMillis),
                )

            lastSessionAt != null ->
                resourceWrapper.getString(
                    R.string.feature_all_trainings_status_last_format,
                    lastSessionAt.relativeAgo(resourceWrapper, nowMillis),
                )

            else -> resourceWrapper.getString(R.string.feature_all_trainings_status_never)
        }
    }

    private fun Long.relativeAgo(
        resourceWrapper: ResourceWrapper,
        nowMillis: Long,
    ): String {
        val deltaMs = (nowMillis - this).coerceAtLeast(0L)
        return when {
            deltaMs < MINUTE_MS -> resourceWrapper.getString(R.string.feature_all_trainings_relative_just_now)
            deltaMs < HOUR_MS -> resourceWrapper.getString(
                R.string.feature_all_trainings_relative_minutes_format,
                deltaMs / MINUTE_MS,
            )

            deltaMs < DAY_MS -> resourceWrapper.getString(
                R.string.feature_all_trainings_relative_hours_format,
                deltaMs / HOUR_MS,
            )

            else -> resourceWrapper.getString(
                R.string.feature_all_trainings_relative_days_format,
                deltaMs / DAY_MS,
            )
        }
    }
}
