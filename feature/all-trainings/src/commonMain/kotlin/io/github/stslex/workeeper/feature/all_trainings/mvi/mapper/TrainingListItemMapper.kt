// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.mvi.mapper

import io.github.stslex.workeeper.feature.all_trainings.domain.model.TrainingListItemDomain
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingListItemUi
import io.github.stslex.workeeper.feature.all_trainings.resources.Res
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_relative_days_format
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_relative_hours_format
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_relative_just_now
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_relative_minutes_format
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_status_in_progress_format
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_status_last_format
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_status_never
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock

object TrainingListItemMapper {

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60 * MINUTE_MS
    private const val DAY_MS = 24 * HOUR_MS

    internal suspend fun TrainingListItemDomain.toUi(
        nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): TrainingListItemUi = TrainingListItemUi(
        uuid = uuid,
        name = name,
        tags = tags.toImmutableList(),
        exerciseCount = exerciseCount,
        isActive = isActive,
        statusLabel = statusLabel(nowMillis),
    )

    private suspend fun TrainingListItemDomain.statusLabel(
        nowMillis: Long,
    ): String {
        val sessionStartedAt = activeSessionStartedAt
        val lastSessionAt = lastSessionAt
        return when {
            isActive && sessionStartedAt != null ->
                getString(
                    Res.string.feature_all_trainings_status_in_progress_format,
                    sessionStartedAt.relativeAgo(nowMillis),
                )

            lastSessionAt != null ->
                getString(
                    Res.string.feature_all_trainings_status_last_format,
                    lastSessionAt.relativeAgo(nowMillis),
                )

            else -> getString(Res.string.feature_all_trainings_status_never)
        }
    }

    private suspend fun Long.relativeAgo(
        nowMillis: Long,
    ): String {
        val deltaMs = (nowMillis - this).coerceAtLeast(0L)
        return when {
            deltaMs < MINUTE_MS -> getString(Res.string.feature_all_trainings_relative_just_now)
            deltaMs < HOUR_MS -> getString(
                Res.string.feature_all_trainings_relative_minutes_format,
                deltaMs / MINUTE_MS,
            )

            deltaMs < DAY_MS -> getString(
                Res.string.feature_all_trainings_relative_hours_format,
                deltaMs / HOUR_MS,
            )

            else -> getString(
                Res.string.feature_all_trainings_relative_days_format,
                deltaMs / DAY_MS,
            )
        }
    }
}
