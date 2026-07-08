// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutScope
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@SingleIn(LiveWorkoutScope::class)
internal class CommonHandler @Inject constructor(
    private val interactor: LiveWorkoutInteractor,
    private val resourceWrapper: ResourceWrapper,
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
            onSuccess = { createdState ->
                if (createdState == null) {
                    updateStateImmediate { it.copy(isLoading = false) }
                    return@launch
                }
                updateStateImmediate { createdState }
                startTimer()
            },
        ) {
            val sessionUuid = current.sessionUuid ?: createSession(current.trainingUuid)
            val now = System.currentTimeMillis()
            sessionUuid
                ?.let { interactor.loadSession(it) }
                ?.toState(
                    nowMillis = now,
                    resourceWrapper = resourceWrapper,
                )
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
            onSuccess = { state ->
                if (state == null) return@launch
                updateStateImmediate { state }
            },
        ) {
            interactor.loadSession(sessionUuid)
                ?.toState(
                    nowMillis = System.currentTimeMillis(),
                    resourceWrapper = resourceWrapper,
                )
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
