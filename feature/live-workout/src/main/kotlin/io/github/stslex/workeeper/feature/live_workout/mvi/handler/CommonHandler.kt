// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutScope
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toState
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.withExpansionCarriedFrom
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
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
            // The plan editor's result. Only a save changes what the session should show,
            // so only a save re-reads it — the branch lives here rather than in the graph,
            // which forwards the result without interpreting it.
            //
            // processReload skips session creation: this can only fire on a session that
            // already exists, because the plan editor is reachable only from inside one.
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
     * The only honest exit when the session did not load: say so and leave.
     *
     * Two arms reach it — a throw, and a `loadSession` that resolves to nothing — and both used to
     * clear `isLoading` and stop. That is worse than it sounds. The route composes on that flag, so
     * clearing it presents the requested session as a **successfully empty** one: "No exercises
     * yet", an Add CTA, and a Finish dock that is enabled by `!isLoading`. A transient read failure
     * could therefore finish a workout whose exercises never loaded.
     *
     * GUARD: clearing the flag is still load-bearing and must stay — `launch` defaults `onError` to
     * `{}` (B17, B21), and a latched flag behind the route gate is a permanently empty frame. It is
     * cleared AND the screen is left, so neither failure mode is reachable.
     */
    private suspend fun abandonUnloadedSession() {
        updateStateImmediate { it.copy(isLoading = false) }
        sendEvent(
            Event.ShowError(
                message = resourceWrapper.getString(
                    R.string.feature_live_workout_error_session_load_failed,
                ),
            ),
        )
        consume(Action.Navigation.Back)
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
            onSuccess = { reloaded ->
                if (reloaded == null) return@launch
                // A plan-editor round-trip is not "leaving the screen session" (§7), so the
                // user's manual expansions must survive this replacement.
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
