// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain.usecase

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.time.weekWindowOf
import io.github.stslex.workeeper.core.core.time.weekdayIndexOf
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.feature.home.di.HomeScope
import io.github.stslex.workeeper.feature.home.domain.model.WeekReadoutDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone

/**
 * Observes the «Неделя» readout: the calendar week containing `nowMillis`, Monday-first. The
 * window is fixed for the lifetime of the collection — see home-start-card.md.
 */
@Inject
@SingleIn(HomeScope::class)
internal class ObserveWeekReadoutUseCase(
    private val sessionRepository: SessionRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<WeekReadoutDomain> {
        val window = weekWindowOf(nowMillis, timeZone)
        return sessionRepository
            .observeFinishedTimesBetween(window.startMillis, window.endMillis)
            .map { times ->
                WeekReadoutDomain(
                    sessionsThisWeek = times.size,
                    trainedDayIndexes = times.mapTo(mutableSetOf()) { weekdayIndexOf(it, timeZone) },
                )
            }
            .flowOn(defaultDispatcher)
    }
}
