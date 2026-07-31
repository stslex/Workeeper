// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.core.time.formatRelativeTime
import io.github.stslex.workeeper.feature.home.R
import io.github.stslex.workeeper.feature.home.domain.model.ActiveSessionWithStatsDomain
import io.github.stslex.workeeper.feature.home.domain.model.RecentSessionDomain
import io.github.stslex.workeeper.feature.home.domain.model.TrainingListItemDomain
import io.github.stslex.workeeper.feature.home.mvi.model.PickerTrainingItem
import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State.ActiveSessionInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal object HomeUiMapper {

    fun ActiveSessionWithStatsDomain.toUi(
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

    /**
     * One row, mapped per paged item.
     *
     * Was `List<RecentSessionDomain>.toRecentItems(…)`, mapping a whole ten-row snapshot at once.
     * Under a `Pager` the unit of mapping is the item, so the list form is gone rather than kept
     * beside this one — two mappers for one row is how the two drift.
     *
     * [nowMillis] is supplied by the caller rather than read here, and `PagingHandler` reads the
     * clock once per `PagingData` generation: rows in one list must agree about what "yesterday"
     * means.
     */
    fun RecentSessionDomain.toRecentItem(
        nowMillis: Long,
        resourceWrapper: ResourceWrapper,
    ): RecentSessionItem {
        val displayName = if (isAdhoc) {
            resourceWrapper.getString(R.string.feature_home_recent_adhoc_label)
        } else {
            trainingName
        }
        val statsLabel = resourceWrapper.getString(
            R.string.feature_home_recent_stats_format,
            resourceWrapper.getQuantityString(
                R.plurals.feature_home_recent_exercises_count,
                exerciseCount,
                exerciseCount,
            ),
            resourceWrapper.getQuantityString(
                R.plurals.feature_home_recent_sets_count,
                setCount,
                setCount,
            ),
        )
        return RecentSessionItem(
            sessionUuid = sessionUuid,
            trainingName = displayName,
            isAdhoc = isAdhoc,
            finishedAtRelativeLabel = formatRelativeTime(nowMillis, finishedAt),
            durationLabel = formatElapsedDuration(finishedAt - startedAt),
            statsLabel = statsLabel,
        )
    }

    fun List<TrainingListItemDomain>.toPickerItems(
        nowMillis: Long,
        resourceWrapper: ResourceWrapper,
    ): ImmutableList<PickerTrainingItem> = map { training ->
        PickerTrainingItem(
            trainingUuid = training.uuid,
            name = training.name,
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
}
