// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.core.utils.DateTimeUtil
import io.github.stslex.workeeper.feature.home.R
import io.github.stslex.workeeper.feature.home.domain.model.ActiveSessionWithStatsDomain
import io.github.stslex.workeeper.feature.home.domain.model.RecentSessionDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartCardReadoutDomain
import io.github.stslex.workeeper.feature.home.domain.model.TrainingListItemDomain
import io.github.stslex.workeeper.feature.home.domain.model.WeekReadoutDomain
import io.github.stslex.workeeper.feature.home.mvi.model.PickerTrainingItem
import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem
import io.github.stslex.workeeper.feature.home.mvi.model.StartCardBodyUi
import io.github.stslex.workeeper.feature.home.mvi.model.TagIdleRowUi
import io.github.stslex.workeeper.feature.home.mvi.model.WeekDayUi
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State.ActiveSessionInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal object HomeUiMapper {

    /** Monday-first, matching `WeekReadoutDomain.trainedDayIndexes` (0 = Monday). */
    private val WEEK_DAY_LABELS = listOf(
        R.string.feature_home_start_week_day_mon,
        R.string.feature_home_start_week_day_tue,
        R.string.feature_home_start_week_day_wed,
        R.string.feature_home_start_week_day_thu,
        R.string.feature_home_start_week_day_fri,
        R.string.feature_home_start_week_day_sat,
        R.string.feature_home_start_week_day_sun,
    )

    fun StartCardReadoutDomain.toUi(resourceWrapper: ResourceWrapper): StartCardBodyUi =
        when (this) {
            is StartCardReadoutDomain.Week -> readout.toUi(resourceWrapper)

            is StartCardReadoutDomain.DaysSince -> StartCardBodyUi.DaysSince(
                daysCountLabel = daysSince.toString(),
                daysUnitLabel = resourceWrapper.getQuantityString(
                    R.plurals.feature_home_start_days_count,
                    daysSince,
                ),
                anchorLabel = resourceWrapper.getString(
                    R.string.feature_home_recent_stats_format,
                    if (lastIsAdhoc) {
                        resourceWrapper.getString(R.string.feature_home_recent_adhoc_label)
                    } else {
                        lastTrainingName
                    },
                    DateTimeUtil.formatMillis(lastFinishedAt),
                ),
            )

            is StartCardReadoutDomain.TagIdle -> {
                val maxIdle = entries.maxOf { it.daysIdle }
                StartCardBodyUi.TagIdle(
                    rows = entries.map { entry ->
                        TagIdleRowUi(
                            name = entry.name,
                            barFraction = if (maxIdle > 0) {
                                entry.daysIdle.toFloat() / maxIdle
                            } else {
                                0f
                            },
                            daysCountLabel = entry.daysIdle.toString(),
                        )
                    }.toImmutableList(),
                    footnoteLabel = resourceWrapper.getString(
                        R.string.feature_home_start_groups_footnote,
                    ),
                )
            }

            is StartCardReadoutDomain.Forgotten -> StartCardBodyUi.Forgotten(
                trainingUuid = trainingUuid,
                trainingName = trainingName,
                metaLabel = resourceWrapper.getString(
                    R.string.feature_home_recent_stats_format,
                    daysIdle?.let { days ->
                        resourceWrapper.getQuantityString(
                            R.plurals.feature_home_start_days_idle_count,
                            days,
                            days,
                        )
                    } ?: resourceWrapper.getString(R.string.feature_home_start_never_run),
                    resourceWrapper.getQuantityString(
                        R.plurals.feature_home_recent_exercises_count,
                        exerciseCount,
                        exerciseCount,
                    ),
                ),
            )

            StartCardReadoutDomain.NoSessions -> StartCardBodyUi.Empty(
                message = resourceWrapper.getString(R.string.feature_home_start_empty_sessions),
            )

            StartCardReadoutDomain.NoTaggedHistory -> StartCardBodyUi.Empty(
                message = resourceWrapper.getString(R.string.feature_home_start_empty_tags),
            )

            // The picker's copy is this state's copy, so the key is shared despite its name.
            StartCardReadoutDomain.NoTemplates -> StartCardBodyUi.Empty(
                message = resourceWrapper.getString(R.string.feature_home_picker_empty),
            )
        }

    fun WeekReadoutDomain.toUi(resourceWrapper: ResourceWrapper): StartCardBodyUi.Week =
        StartCardBodyUi.Week(
            sessionsCountLabel = sessionsThisWeek.toString(),
            sessionsUnitLabel = resourceWrapper.getQuantityString(
                R.plurals.feature_home_start_week_sessions_count,
                sessionsThisWeek,
            ),
            days = WEEK_DAY_LABELS.mapIndexed { index, labelRes ->
                WeekDayUi(
                    label = resourceWrapper.getString(labelRes),
                    isFilled = index in trainedDayIndexes,
                )
            }.toImmutableList(),
        )

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

    /** One row per paged item; [nowMillis] comes from the caller so rows share one clock read. */
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
            finishedAtRelativeLabel = resourceWrapper.getAbbreviatedRelativeTime(finishedAt, nowMillis),
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
                resourceWrapper.getAbbreviatedRelativeTime(it, nowMillis)
            },
        )
    }.toImmutableList()
}
