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
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.withExpansionCarriedFrom
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
            // Only a save changes what the session shows, so only a save re-reads it.
            is Action.Common.PlanResultReceived -> if (action.saved) processReload()
        }
    }

    private fun processInit() {
        val current = state.value
        launch(
            onSuccess = { createdState ->
                if (createdState == null) {
                    abandonUnloadedSession()
                    return@launch
                }
                updateStateImmediate { previous ->
                    createdState.withExpansionCarriedFrom(previous)
                }
                startTimer()
            },
            onError = { abandonUnloadedSession() },
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

    /**
     * The only honest exit when the session did not load. GUARD: set both flags, and record the
     * failure in State, never as an event. See documentation/architecture.md.
     */
    private suspend fun abandonUnloadedSession() {
        updateStateImmediate { it.copy(isLoading = false, loadFailed = true) }
    }

    private suspend fun createSession(trainingUuid: String?): String? {
        if (trainingUuid.isNullOrBlank()) {
            // Blank-init branch (Quick start "Start blank"): both route args are null, so
            // mint a fresh ad-hoc training plus an empty IN_PROGRESS session.
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
            onSuccess = { reloaded ->
                if (reloaded == null) return@launch
                // A plan-editor round-trip is not leaving the session (§7); expansions survive.
                updateStateImmediate { previous -> reloaded.withExpansionCarriedFrom(previous) }
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
