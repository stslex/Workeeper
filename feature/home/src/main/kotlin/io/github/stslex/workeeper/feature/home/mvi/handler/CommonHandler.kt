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
import kotlinx.coroutines.isActive
import javax.inject.Inject

private const val TIMER_TICK_MS = 1000L
private const val HOME_RECENT_LIMIT = 10

@ViewModelScoped
internal class CommonHandler @Inject constructor(
    private val interactor: HomeInteractor,
    private val resourceWrapper: ResourceWrapper,
    store: HomeHandlerStore,
) : Handler<Action.Common>, HomeHandlerStore by store {

    override fun invoke(action: Action.Common) {
        when (action) {
            Action.Common.Init -> processInit()
            Action.Common.TimerTick -> processTimerTick()
        }
    }

    private fun processInit() {
        scope.launch(interactor.observeActiveSession()) { row ->
            updateStateImmediate { current ->
                val now =
                    if (current.nowMillis == 0L) System.currentTimeMillis() else current.nowMillis
                current.copy(
                    activeSession = row?.toUi(now, resourceWrapper),
                    isActiveLoaded = true,
                    nowMillis = now,
                )
            }
        }
        scope.launch(interactor.observeRecent(HOME_RECENT_LIMIT)) { sessions ->
            updateStateImmediate { current ->
                val now =
                    if (current.nowMillis == 0L) System.currentTimeMillis() else current.nowMillis
                current.copy(
                    recent = sessions.toRecentItems(now, resourceWrapper),
                    isRecentLoaded = true,
                    nowMillis = now,
                )
            }
        }
        scope.launch(
            workDispatcher = scope.defaultDispatcher,
            eachDispatcher = scope.defaultDispatcher,
            action = {
                while (isActive) {
                    delay(TIMER_TICK_MS)
                    consume(Action.Common.TimerTick)
                }
            },
        )
    }

    private fun processTimerTick() {
        updateState { current ->
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
    }
}
