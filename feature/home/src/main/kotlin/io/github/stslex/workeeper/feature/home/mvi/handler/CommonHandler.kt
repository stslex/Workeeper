// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.domain.HomeInteractor
import io.github.stslex.workeeper.feature.home.mvi.mapper.toRecentItems
import io.github.stslex.workeeper.feature.home.mvi.mapper.toUi
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

@ViewModelScoped
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
            "Home screen initialized, observing active session and recent sessions."
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
        interactor.observeRecent(HOME_RECENT_LIMIT).launch { sessions ->
            logger.i {
                "Received update for recent sessions: ${sessions.size} sessions. " +
                    "Updating state with new recent sessions data."
            }
            updateStateImmediate { current ->
                val now = if (current.nowMillis == 0L) {
                    System.currentTimeMillis()
                } else {
                    current.nowMillis
                }
                current.copy(
                    recent = sessions.toRecentItems(now, resourceWrapper),
                    isRecentLoaded = true,
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
        private const val HOME_RECENT_LIMIT = 10
    }
}
