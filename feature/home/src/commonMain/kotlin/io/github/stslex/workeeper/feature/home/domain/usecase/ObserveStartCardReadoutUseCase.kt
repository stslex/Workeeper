// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain.usecase

import io.github.stslex.workeeper.feature.home.domain.model.StartCardModeDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartCardReadoutDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.TimeZone

/**
 * Observes the selected mode's readout (home-start-card.md §3) — the mode's data or its own
 * empty state, never a fallback onto a sibling mode (HD2–HD4).
 */
interface ObserveStartCardReadoutUseCase {

    operator fun invoke(
        mode: StartCardModeDomain,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<StartCardReadoutDomain>
}
