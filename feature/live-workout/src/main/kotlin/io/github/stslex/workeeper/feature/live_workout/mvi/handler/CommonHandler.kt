// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_unit_reps
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutScope
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionSnapshotDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveSetMutator
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toState
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toUi
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.withExpansionCarriedFrom
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExternalSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

@SingleIn(LiveWorkoutScope::class)
internal class CommonHandler @Inject constructor(
    private val interactor: LiveWorkoutInteractor,
    private val resourceWrapper: ResourceWrapper,
    private val setMutator: LiveSetMutator,
    private val storeDispatchers: StoreDispatchers,
    store: LiveWorkoutHandlerStore,
) : Handler<Action.Common>, LiveWorkoutHandlerStore by store {

    private var startTimerJob: Job? = null
    private var externalWritesJob: Job? = null
    private val coverage = ExternalWriteCoverage()

    /** Completed once the latest Init's subscription is active; a reload waits for it. */
    private var subscribed = CompletableDeferred<Unit>()

    override fun invoke(action: Action.Common) {
        when (action) {
            Action.Common.Init -> processInit()
            // Only a save changes what the session shows, so only a save re-reads it.
            is Action.Common.PlanResultReceived -> if (action.saved) processReload()
        }
    }

    private fun processInit() {
        val current = state.value
        // GUARD: Init and PlanResultReceived are both consumed on the main thread, so a reload
        // consumed after this Init always waits for this Init's subscription.
        if (subscribed.isCompleted) subscribed = CompletableDeferred()
        launch(onError = { abandonUnloadedSession() }) {
            val sessionUuid = current.sessionUuid ?: createSession(current.trainingUuid)
            if (sessionUuid == null) {
                onMain { abandonUnloadedSession() }
                return@launch
            }
            val initScope = this
            onMain { restartExternalWrites(initScope, sessionUuid) }
            val loaded = coveredLoad { interactor.loadSession(sessionUuid)?.toLoadedState() }
            onMain { if (loaded) startTimer() else abandonUnloadedSession() }
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
        val subscription = subscribed
        launch {
            // As the Init load: no read before the subscription is active, or a watch write committed
            // between the two would reach nobody, and a later apply of this result would hide it.
            subscription.await()
            coveredLoad { interactor.loadSession(sessionUuid)?.toLoadedState() }
        }
    }

    private suspend fun SessionSnapshotDomain.toLoadedState(): State = toState(
        nowMillis = System.currentTimeMillis(),
        resourceWrapper = resourceWrapper,
        // kit's unit is a suspend-only CMP read: resolved here, at the load
        // boundary, so the mapper stays a pure synchronous transformation.
        repsUnitLabel = getString(Res.string.core_ui_kit_plan_editor_unit_reps),
    )

    /**
     * One load under wear-live-sync.md §6.4 "Loads": the watch writes received after its read
     * started are applied again right after its result, in the same state update, so the result
     * cannot hide them. False when the read found no session.
     */
    private suspend fun coveredLoad(read: suspend () -> State?): Boolean {
        val load = ExternalWriteCoverage.Load()
        // GUARD: begin inside the try. A dispose between the begin block and the hop back still throws
        // out of withContext, after the block ran; the finally must end that load too.
        try {
            onMain { coverage.begin(load) }
            val loaded = read() ?: return false
            onMain {
                // A plan-editor round-trip is not leaving the session (§7); expansions survive.
                updateStateImmediate { previous ->
                    coverage.since(load).fold(loaded.withExpansionCarriedFrom(previous), setMutator::applyExternalSet)
                }
            }
            return true
        } finally {
            withContext(NonCancellable + storeDispatchers.mainImmediateDispatcher) { coverage.end(load) }
        }
    }

    /**
     * D10 (wear-live-sync.md §6.4): every Init restarts this subscription, as it restarts the timer,
     * because the store's scope ends whenever the screen leaves composition. It is started
     * undispatched, so it is active before this Init's load starts and no write can fall between.
     *
     * GUARD: if Init stops running on each return (the latch tech-debt.md proposes), this
     * subscription and the timer must move to the return path; the return-to-screen test fails
     * until they do.
     */
    private fun restartExternalWrites(scope: CoroutineScope, sessionUuid: String) {
        externalWritesJob?.cancel()
        externalWritesJob = scope.launch(storeDispatchers.mainImmediateDispatcher, CoroutineStart.UNDISPATCHED) {
            interactor.observeExternalSetWrites(sessionUuid)
                .map { write -> write.toUi() }
                .onEach(::applyExternalWrite)
                .catch { failure -> logger.w { "external set writes stopped: ${failure::class.simpleName}" } }
                .collect()
        }
        subscribed.complete(Unit)
    }

    /** Applied at once (§6.4 A to C), and kept while a load is in flight. */
    private suspend fun applyExternalWrite(write: ExternalSetUiModel) {
        coverage.receive(write)
        updateStateImmediate { latest -> setMutator.applyExternalSet(latest, write) }
    }

    private suspend fun <T> onMain(block: suspend () -> T): T =
        withContext(storeDispatchers.mainImmediateDispatcher) { block() }

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
