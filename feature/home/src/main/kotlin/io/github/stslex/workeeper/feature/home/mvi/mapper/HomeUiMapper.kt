// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.core.time.formatRelativeTime
import io.github.stslex.workeeper.core.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.exercise.session.model.RecentSessionDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingListItem
import io.github.stslex.workeeper.feature.home.R
import io.github.stslex.workeeper.feature.home.mvi.model.PickerTrainingItem
import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State.ActiveSessionInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal fun SessionRepository.ActiveSessionWithStats.toUi(
    nowMillis: Long,
    resourceWrapper: ResourceWrapper,
): ActiveSessionInfo = ActiveSessionInfo(
    sessionUuid = sessionUuid,
    trainingUuid = trainingUuid,
    trainingName = if (isAdhoc) {
        resourceWrapper.getString(R.string.feature_home_recent_adhoc_label)
    } else {
        trainingName
    },
    startedAt = startedAt,
    doneCount = doneCount,
    totalCount = totalCount,
    elapsedDurationLabel = formatElapsedDuration(nowMillis - startedAt),
)

internal fun List<RecentSessionDataModel>.toRecentItems(
    nowMillis: Long,
    resourceWrapper: ResourceWrapper,
): ImmutableList<RecentSessionItem> = map { session ->
    val trainingName = if (session.isAdhoc) {
        resourceWrapper.getString(R.string.feature_home_recent_adhoc_label)
    } else {
        session.trainingName
    }
    val statsLabel = resourceWrapper.getString(
        R.string.feature_home_recent_stats_format,
        resourceWrapper.getQuantityString(
            R.plurals.feature_home_recent_exercises_count,
            session.exerciseCount,
            session.exerciseCount,
        ),
        resourceWrapper.getQuantityString(
            R.plurals.feature_home_recent_sets_count,
            session.setCount,
            session.setCount,
        ),
    )
    RecentSessionItem(
        sessionUuid = session.sessionUuid,
        trainingName = trainingName,
        isAdhoc = session.isAdhoc,
        finishedAtRelativeLabel = formatRelativeTime(nowMillis, session.finishedAt),
        durationLabel = formatElapsedDuration(session.finishedAt - session.startedAt),
        statsLabel = statsLabel,
    )
}.toImmutableList()

internal fun List<TrainingListItem>.toPickerItems(
    nowMillis: Long,
    resourceWrapper: ResourceWrapper,
): ImmutableList<PickerTrainingItem> = map { training ->
    PickerTrainingItem(
        trainingUuid = training.data.uuid,
        name = training.data.name,
        exerciseCountLabel = resourceWrapper.getQuantityString(
            R.plurals.feature_home_recent_exercises_count,
            training.exerciseCount,
            training.exerciseCount,
        ),
        lastSessionRelativeLabel = training.lastSessionAt?.let {
            formatRelativeTime(nowMillis, it)
        },
    )
}.toImmutableList()
