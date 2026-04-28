// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.toState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.inject.Inject

private const val TIMER_TICK_MS = 1000L

@ViewModelScoped
internal class CommonHandler @Inject constructor(
    private val interactor: LiveWorkoutInteractor,
    private val resourceWrapper: ResourceWrapper,
    store: LiveWorkoutHandlerStore,
) : Handler<Action.Common>, LiveWorkoutHandlerStore by store {

    override fun invoke(action: Action.Common) {
        when (action) {
            Action.Common.Init -> processInit()
            Action.Common.TimerTick -> processTimerTick()
        }
    }

    private fun processInit() {
        val current = state.value
        launch(
            onSuccess = { snapshot ->
                if (snapshot == null) {
                    updateStateImmediate { it.copy(isLoading = false) }
                    return@launch
                }
                val now = System.currentTimeMillis()
                updateStateImmediate {
                    snapshot.toState(
                        nowMillis = now,
                        resourceWrapper = resourceWrapper,
                    )
                }
                startTimer()
            },
        ) {
            val sessionUuid = current.sessionUuid ?: createSession(current.trainingUuid)
            sessionUuid?.let { interactor.loadSession(it) }
        }
    }

    private suspend fun createSession(trainingUuid: String?): String? {
        if (trainingUuid.isNullOrBlank()) return null
        return interactor.startSession(trainingUuid)
    }

    private fun processTimerTick() {
        updateState { current ->
            if (current.startedAt <= 0L) current
            else {
                val now = System.currentTimeMillis()
                current.copy(
                    nowMillis = now,
                    elapsedDurationLabel = formatElapsedDuration(now - current.startedAt),
                )
            }
        }
    }

    private fun startTimer() {
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
}
