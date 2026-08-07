// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.di.HomeScope
import io.github.stslex.workeeper.feature.home.domain.HomeInteractor
import io.github.stslex.workeeper.feature.home.mvi.mapper.HomeUiMapper.toUi
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@SingleIn(HomeScope::class)
internal class CommonHandler @Inject constructor(
    private val interactor: HomeInteractor,
    private val resourceWrapper: ResourceWrapper,
    store: HomeHandlerStore,
) : Handler<Action.Common>, HomeHandlerStore by store {

    override fun invoke(action: Action.Common) {
        when (action) {
            Action.Common.Init -> processInit()
        }
    }

    private fun processInit() {
        logger.i {
            "Home screen initialized, observing active session. The recent list is paged and " +
                "collects itself from State.pagingUiState — see PagingHandler."
        }
        interactor.observeWeekReadout(System.currentTimeMillis()).launch { readout ->
            val body = readout.toUi(resourceWrapper)
            updateStateImmediate { current -> current.copy(startCardBody = body) }
        }
        interactor.observeActiveSession().launch { row ->
            logger.i {
                "Received update for active session: ${row ?: "null"}. Updating state with new active session data."
            }
            updateStateImmediate { current ->
                val now = if (current.nowMillis == 0L) {
                    System.currentTimeMillis()
                } else {
                    current.nowMillis
                }
                current.copy(
                    activeSession = row?.toUi(now, resourceWrapper),
                    isActiveLoaded = true,
                    nowMillis = now,
                )
            }
        }
        state
            .distinctUntilChanged { old, new -> (new.activeSession == null) == (old.activeSession == null) }
            .launch {
                if (state.value.activeSession != null) {
                    logger.v {
                        "Active session is present. " +
                            "Starting timer tick loop to update elapsed duration every second."
                    }

                    while (state.value.activeSession != null) {
                        updateStateImmediate { current ->
                            val now = System.currentTimeMillis()
                            current.copy(
                                nowMillis = now,
                                activeSession = current.activeSession?.copy(
                                    elapsedDurationLabel = formatElapsedDuration(
                                        current.activeSession.elapsedMillis(now),
                                    ),
                                ),
                            )
                        }
                        delay(TIMER_TICK_MS)
                    }
                } else {
                    logger.v { "No active session. Timer tick loop will not be started." }
                }
            }
    }

    companion object {
        private const val TIMER_TICK_MS = 1000L
    }
}
