// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain.usecase

import io.github.stslex.workeeper.feature.home.domain.model.WeekReadoutDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.TimeZone

/**
 * Observes the «Неделя» readout: the calendar week containing `nowMillis`, Monday-first. The
 * window is fixed for the lifetime of the collection — see home-start-card.md.
 */
interface ObserveWeekReadoutUseCase {

    operator fun invoke(
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<WeekReadoutDomain>
}
