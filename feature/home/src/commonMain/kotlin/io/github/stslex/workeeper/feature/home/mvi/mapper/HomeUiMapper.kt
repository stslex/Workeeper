// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.core.utils.DateTimeUtil
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
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
import io.github.stslex.workeeper.feature.home.resources.Res
import io.github.stslex.workeeper.feature.home.resources.feature_home_picker_empty
import io.github.stslex.workeeper.feature.home.resources.feature_home_recent_adhoc_label
import io.github.stslex.workeeper.feature.home.resources.feature_home_recent_exercises_count
import io.github.stslex.workeeper.feature.home.resources.feature_home_recent_sets_count
import io.github.stslex.workeeper.feature.home.resources.feature_home_recent_stats_format
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_days_count
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_days_idle_count
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_empty_sessions
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_empty_tags
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_groups_footnote
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_never_run
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_fri
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_mon
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_sat
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_sun
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_thu
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_tue
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_wed
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_sessions_count
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal object HomeUiMapper {

    /** Monday-first, matching `WeekReadoutDomain.trainedDayIndexes` (0 = Monday). */
    private val WEEK_DAY_LABELS = listOf(
        Res.string.feature_home_start_week_day_mon,
        Res.string.feature_home_start_week_day_tue,
        Res.string.feature_home_start_week_day_wed,
        Res.string.feature_home_start_week_day_thu,
        Res.string.feature_home_start_week_day_fri,
        Res.string.feature_home_start_week_day_sat,
        Res.string.feature_home_start_week_day_sun,
    )

    suspend fun StartCardReadoutDomain.toUi(stringResolver: StringResolver): StartCardBodyUi =
        when (this) {
            is StartCardReadoutDomain.Week -> readout.toUi(stringResolver)

            is StartCardReadoutDomain.DaysSince -> StartCardBodyUi.DaysSince(
                daysCountLabel = daysSince.toString(),
                daysUnitLabel = stringResolver.getPluralString(
                    Res.plurals.feature_home_start_days_count,
                    daysSince,
                ),
                anchorLabel = stringResolver.getString(
                    Res.string.feature_home_recent_stats_format,
                    if (lastIsAdhoc) {
                        stringResolver.getString(Res.string.feature_home_recent_adhoc_label)
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
                    footnoteLabel = stringResolver.getString(
                        Res.string.feature_home_start_groups_footnote,
                    ),
                )
            }

            is StartCardReadoutDomain.Forgotten -> StartCardBodyUi.Forgotten(
                trainingUuid = trainingUuid,
                trainingName = trainingName,
                metaLabel = stringResolver.getString(
                    Res.string.feature_home_recent_stats_format,
                    daysIdle?.let { days ->
                        stringResolver.getPluralString(
                            Res.plurals.feature_home_start_days_idle_count,
                            days,
                            days,
                        )
                    } ?: stringResolver.getString(Res.string.feature_home_start_never_run),
                    stringResolver.getPluralString(
                        Res.plurals.feature_home_recent_exercises_count,
                        exerciseCount,
                        exerciseCount,
                    ),
                ),
            )

            StartCardReadoutDomain.NoSessions -> StartCardBodyUi.Empty(
                message = stringResolver.getString(Res.string.feature_home_start_empty_sessions),
            )

            StartCardReadoutDomain.NoTaggedHistory -> StartCardBodyUi.Empty(
                message = stringResolver.getString(Res.string.feature_home_start_empty_tags),
            )

            // The picker's copy is this state's copy, so the key is shared despite its name.
            StartCardReadoutDomain.NoTemplates -> StartCardBodyUi.Empty(
                message = stringResolver.getString(Res.string.feature_home_picker_empty),
            )
        }

    suspend fun WeekReadoutDomain.toUi(stringResolver: StringResolver): StartCardBodyUi.Week =
        StartCardBodyUi.Week(
            sessionsCountLabel = sessionsThisWeek.toString(),
            sessionsUnitLabel = stringResolver.getPluralString(
                Res.plurals.feature_home_start_week_sessions_count,
                sessionsThisWeek,
            ),
            days = WEEK_DAY_LABELS.mapIndexed { index, labelRes ->
                WeekDayUi(
                    label = stringResolver.getString(labelRes),
                    isFilled = index in trainedDayIndexes,
                )
            }.toImmutableList(),
        )

    suspend fun ActiveSessionWithStatsDomain.toUi(
        nowMillis: Long,
        stringResolver: StringResolver,
    ): ActiveSessionInfo = ActiveSessionInfo(
        sessionUuid = sessionUuid,
        trainingUuid = trainingUuid,
        trainingName = if (isAdhoc) {
            stringResolver.getString(Res.string.feature_home_recent_adhoc_label)
        } else {
            trainingName
        },
        startedAt = startedAt,
        doneCount = doneCount,
        totalCount = totalCount,
        elapsedDurationLabel = formatElapsedDuration(nowMillis - startedAt),
    )

    /** One row per paged item; [nowMillis] comes from the caller so rows share one clock read. */
    suspend fun RecentSessionDomain.toRecentItem(
        nowMillis: Long,
        resourceWrapper: ResourceWrapper,
        stringResolver: StringResolver,
    ): RecentSessionItem {
        val displayName = if (isAdhoc) {
            stringResolver.getString(Res.string.feature_home_recent_adhoc_label)
        } else {
            trainingName
        }
        val statsLabel = stringResolver.getString(
            Res.string.feature_home_recent_stats_format,
            stringResolver.getPluralString(
                Res.plurals.feature_home_recent_exercises_count,
                exerciseCount,
                exerciseCount,
            ),
            stringResolver.getPluralString(
                Res.plurals.feature_home_recent_sets_count,
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

    suspend fun List<TrainingListItemDomain>.toPickerItems(
        nowMillis: Long,
        resourceWrapper: ResourceWrapper,
        stringResolver: StringResolver,
    ): ImmutableList<PickerTrainingItem> = map { training ->
        PickerTrainingItem(
            trainingUuid = training.uuid,
            name = training.name,
            exerciseCountLabel = stringResolver.getPluralString(
                Res.plurals.feature_home_recent_exercises_count,
                training.exerciseCount,
                training.exerciseCount,
            ),
            lastSessionRelativeLabel = training.lastSessionAt?.let {
                resourceWrapper.getAbbreviatedRelativeTime(it, nowMillis)
            },
        )
    }.toImmutableList()
}
