// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.domain.HomeInteractor
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.inject.Inject

private const val TIMER_TICK_MS = 1000L

@ViewModelScoped
internal class CommonHandler @Inject constructor(
    private val interactor: HomeInteractor,
    store: HomeHandlerStore,
) : Handler<Action.Common>, HomeHandlerStore by store {

    override fun invoke(action: Action.Common) {
        when (action) {
            Action.Common.Init -> processInit()
            Action.Common.TimerTick -> processTimerTick()
        }
    }

    private fun processInit() {
        scope.launch(interactor.observeActiveSession()) { session ->
            updateStateImmediate { current ->
                current.copy(
                    activeSession = session,
                    isLoading = false,
                    nowMillis = if (current.nowMillis == 0L) System.currentTimeMillis() else current.nowMillis,
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
            current.copy(nowMillis = System.currentTimeMillis())
        }
    }
}
