// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.NavigatorStack
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.toState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import javax.inject.Inject

@ViewModelScoped
internal class CommonHandler @Inject constructor(
    private val interactor: LiveWorkoutInteractor,
    private val resourceWrapper: ResourceWrapper,
    private val navigatorStack: NavigatorStack,
    store: LiveWorkoutHandlerStore,
) : Handler<Action.Common>, LiveWorkoutHandlerStore by store {

    private var startTimerJob: Job? = null

    override fun invoke(action: Action.Common) {
        when (action) {
            Action.Common.Init -> processInit()
            // Reload re-runs the session-load pipeline. Used after returning from the
            // PlanEditor route so the LiveExerciseCard.planSets reflect the new draft.
            // We skip session creation since this fires only on an existing session.
            Action.Common.Reload -> processReload()
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

        navigatorStack
            .subscribeToStackAttr(Screen.PlanEditor.planEditorSavedAttr)
            ?.filterNotNull()
            ?.distinctUntilChanged()
            ?.launch { saved ->
                if (saved) {
                    consume(Action.Common.Reload)
                    navigatorStack.setCurrentStack(Screen.PlanEditor.planEditorSavedAttr)
                }
            }
    }

    private suspend fun createSession(trainingUuid: String?): String? {
        if (trainingUuid.isNullOrBlank()) {
            // Blank-init branch (v2.3 Quick start "Start blank"): both route args are null,
            // so we mint a fresh ad-hoc training + IN_PROGRESS session with no exercises.
            // Subsequent `loadSession` returns an empty snapshot which the screen renders as
            // the empty state ("No exercises yet" + Add exercise CTA).
            return interactor.createAdhocSession(
                name = "",
                exerciseUuids = emptyList(),
            ).sessionUuid
        }
        return interactor.startSession(trainingUuid)
    }

    private fun processReload() {
        val sessionUuid = state.value.sessionUuid?.takeIf { it.isNotBlank() } ?: return
        launch(
            onSuccess = { snapshot ->
                if (snapshot == null) return@launch
                val now = System.currentTimeMillis()
                updateStateImmediate {
                    snapshot.toState(
                        nowMillis = now,
                        resourceWrapper = resourceWrapper,
                    )
                }
            },
        ) {
            interactor.loadSession(sessionUuid)
        }
    }

    private fun startTimer() {
        startTimerJob?.cancel()
        startTimerJob = launch {
            while (isActive) {
                updateStateImmediate { current ->
                    if (current.startedAt <= 0L) current
                    else {
                        val now = System.currentTimeMillis()
                        current.copy(
                            nowMillis = now,
                            elapsedDurationLabel = formatElapsedDuration(now - current.startedAt),
                        )
                    }
                }
                delay(TIMER_TICK_MS)
            }
        }
    }

    companion object {

        private const val TIMER_TICK_MS = 1000L
    }
}
