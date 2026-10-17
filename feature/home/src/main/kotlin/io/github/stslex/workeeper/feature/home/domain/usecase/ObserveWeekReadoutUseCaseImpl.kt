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

/** The production [ObserveWeekReadoutUseCase], bound in `HomeGraph` (D2). */
@Inject
@SingleIn(HomeScope::class)
class ObserveWeekReadoutUseCaseImpl internal constructor(
    private val sessionRepository: SessionRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ObserveWeekReadoutUseCase {

    override operator fun invoke(
        nowMillis: Long,
        timeZone: TimeZone,
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
