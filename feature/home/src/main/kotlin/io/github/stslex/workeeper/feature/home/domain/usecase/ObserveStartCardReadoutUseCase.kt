// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain.usecase

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.time.calendarDaysBetween
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.home.di.HomeScope
import io.github.stslex.workeeper.feature.home.domain.model.StartCardModeDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartCardReadoutDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone

/** «Отставшие группы» shows up to three tags (home-start-card.md §3.3). */
private const val TAG_IDLE_LIMIT = 3

/**
 * Observes the selected mode's readout (home-start-card.md §3) — the mode's data or its own
 * empty state, never a fallback onto a sibling mode (HD2–HD4).
 */
@Inject
@SingleIn(HomeScope::class)
internal class ObserveStartCardReadoutUseCase(
    private val sessionRepository: SessionRepository,
    private val tagRepository: TagRepository,
    private val trainingRepository: TrainingRepository,
    private val observeWeekReadoutUseCase: ObserveWeekReadoutUseCase,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(
        mode: StartCardModeDomain,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<StartCardReadoutDomain> = when (mode) {
        StartCardModeDomain.WEEK -> observeWeek(nowMillis, timeZone)
        StartCardModeDomain.DAYS_SINCE_LAST -> observeDaysSince(nowMillis, timeZone)
        StartCardModeDomain.LAGGING_GROUPS -> observeTagIdle(nowMillis, timeZone)
        StartCardModeDomain.FORGOTTEN_TRAINING -> observeForgotten(nowMillis, timeZone)
    }.flowOn(defaultDispatcher)

    private fun observeWeek(
        nowMillis: Long,
        timeZone: TimeZone,
    ): Flow<StartCardReadoutDomain> = combine(
        observeWeekReadoutUseCase(nowMillis, timeZone),
        sessionRepository.observeLastFinishedSession(),
    ) { week, last ->
        if (last == null) {
            StartCardReadoutDomain.NoSessions
        } else {
            StartCardReadoutDomain.Week(week)
        }
    }

    private fun observeDaysSince(
        nowMillis: Long,
        timeZone: TimeZone,
    ): Flow<StartCardReadoutDomain> = sessionRepository
        .observeLastFinishedSession()
        .map { last ->
            if (last == null) {
                StartCardReadoutDomain.NoSessions
            } else {
                StartCardReadoutDomain.DaysSince(
                    daysSince = calendarDaysBetween(last.finishedAt, nowMillis, timeZone),
                    lastTrainingName = last.trainingName,
                    lastIsAdhoc = last.isAdhoc,
                    lastFinishedAt = last.finishedAt,
                )
            }
        }

    private fun observeTagIdle(
        nowMillis: Long,
        timeZone: TimeZone,
    ): Flow<StartCardReadoutDomain> = tagRepository
        .observeTagIdleStats(TAG_IDLE_LIMIT)
        .map { stats ->
            if (stats.isEmpty()) {
                StartCardReadoutDomain.NoTaggedHistory
            } else {
                StartCardReadoutDomain.TagIdle(
                    entries = stats.map { stat ->
                        StartCardReadoutDomain.TagIdle.Entry(
                            name = stat.name,
                            daysIdle = calendarDaysBetween(stat.lastTrainedAt, nowMillis, timeZone),
                        )
                    },
                )
            }
        }

    private fun observeForgotten(
        nowMillis: Long,
        timeZone: TimeZone,
    ): Flow<StartCardReadoutDomain> = trainingRepository
        .observeMostForgottenTemplate()
        .map { template ->
            if (template == null) {
                StartCardReadoutDomain.NoTemplates
            } else {
                StartCardReadoutDomain.Forgotten(
                    trainingUuid = template.data.uuid,
                    trainingName = template.data.name,
                    daysIdle = template.lastSessionAt?.let { last ->
                        calendarDaysBetween(last, nowMillis, timeZone)
                    },
                    exerciseCount = template.exerciseCount,
                )
            }
        }
}
