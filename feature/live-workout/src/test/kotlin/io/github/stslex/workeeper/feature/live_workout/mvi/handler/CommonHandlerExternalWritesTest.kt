// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStoreImpl
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.domain.model.AdhocSessionResult
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExternalSetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.LiveExerciseDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PerformedExerciseDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionSnapshotDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionStateDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SetTypeDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.PendingUndoOps.undoPending
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveSetMutator
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.StateStatusMapper
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.DialogState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.github.stslex.workeeper.feature.live_workout.mvi.store.PendingUndo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Collections
import java.util.concurrent.BlockingDeque
import java.util.concurrent.LinkedBlockingDeque
import kotlin.coroutines.CoroutineContext

/**
 * wear-live-sync.md §6.4 (D10) and §10.1, the live-workout half: the real [BaseStore] lifecycle (every
 * `init` consumes `Init`, every `dispose` ends the scope) around the real [CommonHandler], with one
 * test dispatcher for every store dispatcher. The interactor is faked: its loads can be held open,
 * and its write flow counts the collectors.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class CommonHandlerExternalWritesTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val lifetime = AppScopeLifetime()
    private val writes = MutableSharedFlow<ExternalSetDomain>(extraBufferCapacity = WRITE_BUFFER)
    private val loads = Loads()
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    private val setMutator = LiveSetMutator(StateStatusMapper(resourceWrapper))
    private val interactor = mockk<LiveWorkoutInteractor>(relaxed = true).apply {
        every { observeExternalSetWrites(SESSION) } returns writes
        coEvery { loadSession(SESSION) } coAnswers { loads.next() }
    }
    private val handlerStore = LiveWorkoutHandlerStoreImpl()

    /** The store's work dispatcher; one test swaps it for [ManualDispatcher] before first use. */
    private var workDispatcher: CoroutineDispatcher = dispatcher

    /** The route's state; the session-creation tests clear its session before first use. */
    private var route: State = State.create(sessionUuid = SESSION, trainingUuid = TRAINING)

    /** Every `updateStateImmediate` call the store received: a read then a write (D14). */
    private val immediateWrites: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val storeDispatchers by lazy { StoreDispatchers(workDispatcher, dispatcher) }
    private val handler by lazy {
        CommonHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            setMutator = setMutator,
            storeDispatchers = storeDispatchers,
            store = handlerStore,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private val store by lazy {
        object : BaseStore<State, Action, Event>(
            name = "LiveWorkoutExternalWritesTest",
            initialState = route,
            storeEmitter = handlerStore,
            handlerCreator = { handler as Handler<Action> },
            initialActions = listOf(Action.Common.Init),
            storeDispatchers = storeDispatchers,
            appScopeLifetime = lifetime,
            analyticsHolder = AnalyticsHolder(),
            loggerHolder = LoggerHolder(),
        ) {
            override suspend fun updateStateImmediate(update: suspend (State) -> State) {
                immediateWrites += "update"
                super.updateStateImmediate(update)
            }

            override suspend fun updateStateImmediate(state: State) {
                immediateWrites += "state"
                super.updateStateImmediate(state)
            }
        }
    }

    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        store.dispose()
        lifetime.cancel()
        Dispatchers.resetMain()
    }

    // region A to C

    @Test
    fun `A - a write for a shown exercise marks that set with the watch's values`() {
        open()

        emit(watchSet(PE_1, position = 1))

        assertShowsWatchSet(store.state.value, PE_1, position = 1)
        assertEquals(1, loads.count, "applied in place, the session was not read again")
    }

    @Test
    fun `A - an open undo window that holds the exercise gets the same patch, so undo keeps the set`() {
        open()
        val before = store.state.value
        store.updateState {
            it.copy(pendingUndo = undoWindow(before, restoreExercises = before.exercises))
        }

        emit(watchSet(PE_1, position = 0))
        assertShowsWatchSet(store.state.value, PE_1, position = 0)
        with(handlerStore) { undoPending(interactor, setMutator, onError = {}) }

        assertNull(store.state.value.pendingUndo)
        assertShowsWatchSet(store.state.value, PE_1, position = 0)
    }

    @Test
    fun `B - a write for a soft-deleted exercise patches only the undo window`() {
        open()
        val before = store.state.value
        store.updateState {
            it.copy(
                exercises = before.exercises.filterNot { exercise -> exercise.performedExerciseUuid == PE_2 }
                    .toImmutableList(),
                pendingUndo = undoWindow(before, restoreExercises = before.exercises),
            )
        }
        val deleted = store.state.value

        emit(watchSet(PE_2, position = 0))

        val after = store.state.value
        assertEquals(deleted.exercises, after.exercises, "the screen does not show the deleted exercise again")
        val restored = requireNotNull(after.pendingUndo).restoreExercises
            .first { it.performedExerciseUuid == PE_2 }
            .performedSets.single { it.position == 0 }
        assertEquals(WATCH_WEIGHT, restored.weight)
        assertEquals(WATCH_REPS, restored.reps)
    }

    @Test
    fun `C - a write for an exercise in neither is ignored`() {
        open()
        val before = store.state.value

        emit(watchSet("pe-unknown", position = 0))

        assertEquals(before, store.state.value)
    }

    @Test
    fun `a patch replaces only that set's draft and leaves every other screen state as it was`() {
        open()
        store.updateState {
            it.copy(
                setDrafts = persistentMapOf(
                    State.DraftKey(PE_1, 0) to draft(position = 0, weight = 90.0, reps = 3),
                    State.DraftKey(PE_1, 1) to draft(position = 1, weight = 95.0, reps = 4),
                    State.DraftKey(PE_2, 0) to draft(position = 0, weight = 60.0, reps = 10),
                ),
                rowCountOverrides = persistentMapOf(PE_2 to 4),
                activeExerciseUuids = persistentSetOf(PE_2),
                expandedExerciseUuids = persistentSetOf(PE_1, PE_2),
                dialogState = DialogState.DeleteDialog(sessionName = "Push Day", progressLabel = "0/2"),
                bottomSheetState = BottomSheetState.ExerciseMenu(PE_2),
                trainingNameDraft = "Push Day B",
                isTrainingNameEditing = true,
            )
        }
        val before = store.state.value

        emit(watchSet(PE_1, position = 0))

        val after = store.state.value
        assertShowsWatchSet(after, PE_1, position = 0)
        assertEquals(before.setDrafts - State.DraftKey(PE_1, 0), after.setDrafts, "only that set's draft yields")
        assertEquals(before.rowCountOverrides, after.rowCountOverrides)
        assertEquals(before.activeExerciseUuids, after.activeExerciseUuids)
        assertEquals(before.expandedExerciseUuids, after.expandedExerciseUuids)
        assertEquals(before.dialogState, after.dialogState)
        assertEquals(before.bottomSheetState, after.bottomSheetState)
        assertEquals(before.trainingNameDraft, after.trainingNameDraft)
        assertEquals(before.isTrainingNameEditing, after.isTrainingNameEditing)
        assertEquals(before.isFinishInFlight, after.isFinishInFlight)
        assertEquals(before.isAddExerciseInFlight, after.isAddExerciseInFlight)
        assertEquals(1, loads.count, "no re-read of the session")
    }

    // endregion

    // region loads (D10)

    @Test
    fun `Init starts its load only once the subscription is active, and a write between them is applied`() {
        // Committed and published just after the load's read began: the read does not hold it.
        loads.onRead = {
            loads.committed += watchSet(PE_1, position = 0)
            check(writes.tryEmit(watchSet(PE_1, position = 0)))
        }

        open()

        assertEquals(listOf(1), loads.subscribersAtRead, "subscribed before the load's read")
        assertShowsWatchSet(store.state.value, PE_1, position = 0)
    }

    @Test
    fun `Init - a write delivered between the load's read and its apply is shown after the load`() {
        loads.gated = true
        open()

        emit(watchSet(PE_1, position = 0))
        loads.release()

        assertShowsWatchSet(store.state.value, PE_1, position = 0)
    }

    @Test
    fun `processReload - a write delivered between the load's read and its apply is shown after the load`() {
        open()
        loads.gated = true
        store.consume(Action.Common.PlanResultReceived(saved = true))
        scheduler.runCurrent()

        emit(watchSet(PE_1, position = 0))
        loads.release()

        assertEquals(2, loads.count)
        assertShowsWatchSet(store.state.value, PE_1, position = 0)
    }

    @Test
    fun `a return to the screen without a save, then a write, shows the write`() {
        open()
        store.dispose()
        scheduler.runCurrent()
        assertEquals(0, writes.subscriptionCount.value, "the store's scope ended")

        store.init(owner)
        scheduler.runCurrent()
        emit(watchSet(PE_1, position = 0))

        assertEquals(1, writes.subscriptionCount.value)
        assertShowsWatchSet(store.state.value, PE_1, position = 0)
    }

    @Test
    fun `a return with a save keeps a write delivered during both loads, the Init load applied last`() {
        returnWithSave(initLoadLast = true)
    }

    @Test
    fun `a return with a save keeps a write delivered during both loads, the reload applied last`() {
        returnWithSave(initLoadLast = false)
    }

    @Test
    fun `a repeated Init without a dispose leaves one active subscription`() {
        open()

        store.consume(Action.Common.Init)
        scheduler.runCurrent()

        assertEquals(1, writes.subscriptionCount.value)
        emit(watchSet(PE_1, position = 0))
        assertShowsWatchSet(store.state.value, PE_1, position = 0)
    }

    /**
     * §6.4: a reload must not read before the subscription is active, or a write committed between
     * the two reaches nobody. On a return with a save both coroutines start on the work dispatcher;
     * here the reload's runs as far as it can before the Init's starts, as a busy thread pool may.
     */
    @Test
    fun `a reload scheduled ahead of the return's Init still reads only with the subscription active`() {
        val work = ManualDispatcher()
        workDispatcher = work
        store.init(owner)
        drain(work)
        store.dispose()
        drain(work)
        loads.subscribersAtRead.clear()

        store.init(owner)
        store.consume(Action.Common.PlanResultReceived(saved = true))
        assertEquals(2, work.queue.size, "the Init and the reload are both waiting for a thread")
        val init = work.queue.removeFirst()
        drain(work)
        work.queue.addLast(init)
        drain(work)

        assertEquals(2, loads.subscribersAtRead.size, "both loads read")
        assertEquals(listOf(1, 1), loads.subscribersAtRead, "each read started with the subscription active")
    }

    @Test
    fun `Init with a plan's training starts the session, subscribes before its load and shows a later write`() {
        route = State.create(sessionUuid = null, trainingUuid = TRAINING)
        coEvery { interactor.startSession(TRAINING) } returns SESSION

        open()

        coVerify(exactly = 1) { interactor.startSession(TRAINING) }
        assertCreatedSessionShowsWrites()
    }

    @Test
    fun `Init from Quick start creates the ad-hoc session, subscribes before its load and shows a later write`() {
        route = State.create(sessionUuid = null, trainingUuid = null)
        coEvery { interactor.createAdhocSession(name = "", exerciseUuids = emptyList()) } returns
            AdhocSessionResult(sessionUuid = SESSION, trainingUuid = TRAINING)

        open()

        coVerify(exactly = 1) { interactor.createAdhocSession(name = "", exerciseUuids = emptyList()) }
        assertCreatedSessionShowsWrites()
    }

    // endregion

    // region atomic writes (D14)

    @Test
    fun `no store write reads then writes across Init, a load, a watch write, timer ticks and a failed load`() {
        open()
        emit(watchSet(PE_1, position = 0))
        val shown = mutableListOf(store.state.value.nowMillis)
        repeat(TIMER_TICKS) {
            // Each tick reads the wall clock; let it move before the next one.
            Thread.sleep(WALL_CLOCK_STEP_MS)
            scheduler.advanceTimeBy(TIMER_TICK_MS)
            scheduler.runCurrent()
            shown += store.state.value.nowMillis
        }
        store.dispose()
        scheduler.runCurrent()
        coEvery { interactor.loadSession(SESSION) } returns null
        store.init(owner)
        scheduler.runCurrent()

        assertTrue(store.state.value.loadFailed, "the failed load set its flags")
        assertEquals(emptyList<String>(), immediateWrites, "every store write is a compare-and-set (D14)")
        assertTrue(shown.zipWithNext().count { (before, after) -> after > before } >= TIMER_TICKS, "$shown")
    }

    // endregion

    /** Runs both dispatchers until neither has work, the work queue in its current order. */
    private fun drain(work: ManualDispatcher) {
        while (true) {
            scheduler.runCurrent()
            val next = work.queue.pollFirst() ?: break
            next.run()
        }
        scheduler.runCurrent()
    }

    /**
     * A work dispatcher the test runs by hand, so it can choose which coroutine a thread runs first.
     * It is no `Delay`, so a delay on it resumes from the default executor's thread in real time: the
     * queue is thread-safe for that dispatch.
     */
    private class ManualDispatcher : CoroutineDispatcher() {
        val queue: BlockingDeque<Runnable> = LinkedBlockingDeque()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }
    }

    private fun returnWithSave(initLoadLast: Boolean) {
        open()
        store.dispose()
        loads.gated = true
        store.init(owner)
        store.consume(Action.Common.PlanResultReceived(saved = true))
        scheduler.runCurrent()
        assertEquals(2, loads.pending.size, "both loads are in flight")

        emit(watchSet(PE_1, position = 0))
        val (first, last) = loads.pending.toList().let { (init, reload) ->
            if (initLoadLast) reload to init else init to reload
        }
        first.complete(snapshot())
        scheduler.runCurrent()
        last.complete(snapshot())
        scheduler.runCurrent()

        assertShowsWatchSet(store.state.value, PE_1, position = 0)
    }

    private fun open() {
        store.init(owner)
        scheduler.runCurrent()
    }

    /** The created session was subscribed to before its load read, and a later write is shown. */
    private fun assertCreatedSessionShowsWrites() {
        assertEquals(SESSION, store.state.value.sessionUuid, "the created session loaded")
        assertEquals(listOf(1), loads.subscribersAtRead, "subscribed once the session existed, before its read")
        emit(watchSet(PE_1, position = 0))
        assertShowsWatchSet(store.state.value, PE_1, position = 0)
    }

    /** The bridge's order: the write commits, then it is published. */
    private fun emit(write: ExternalSetDomain) {
        loads.committed += write
        check(writes.tryEmit(write))
        scheduler.runCurrent()
    }

    private fun assertShowsWatchSet(state: State, exercise: String, position: Int) {
        val set = state.exercises.firstOrNull { it.performedExerciseUuid == exercise }
            ?.performedSets?.firstOrNull { it.position == position }
        requireNotNull(set) { "the watch's set is not shown: ${state.exercises.map { it.performedSets }}" }
        assertEquals(WATCH_WEIGHT, set.weight)
        assertEquals(WATCH_REPS, set.reps)
        assertTrue(set.isDone)
    }

    private fun undoWindow(state: State, restoreExercises: List<LiveExerciseUiModel>) =
        PendingUndo(
            id = 1L,
            message = "",
            restoreExercises = restoreExercises.toImmutableList(),
            restoreDrafts = state.setDrafts,
            restoreOverrides = state.rowCountOverrides,
        )

    /**
     * The test's loads: answered at once with what the database holds (every committed watch write
     * included), or held until [release] or a test completes them with an earlier read.
     */
    private inner class Loads {
        var gated = false
        var count = 0
        var onRead: () -> Unit = {}
        val committed = mutableListOf<ExternalSetDomain>()
        val subscribersAtRead = mutableListOf<Int>()
        val pending = ArrayDeque<CompletableDeferred<SessionSnapshotDomain>>()

        suspend fun next(): SessionSnapshotDomain {
            count += 1
            subscribersAtRead += writes.subscriptionCount.value
            val read = snapshot(committed.toList())
            // One-shot: a mutation that re-reads on every write would otherwise loop forever here.
            val hook = onRead
            onRead = {}
            hook()
            if (!gated) return read
            val gate = CompletableDeferred<SessionSnapshotDomain>()
            pending.addLast(gate)
            return gate.await()
        }

        /** Every held load returns what the database held before the watch's write. */
        fun release() {
            pending.forEach { it.complete(snapshot()) }
            scheduler.runCurrent()
        }
    }

    private companion object {
        const val SESSION = "session-1"
        const val TRAINING = "training-1"
        const val PE_1 = "pe-1"
        const val PE_2 = "pe-2"
        const val WATCH_WEIGHT = 105.0
        const val WATCH_REPS = 8
        const val WRITE_BUFFER = 16
        const val TIMER_TICK_MS = 1_000L
        const val TIMER_TICKS = 2
        const val WALL_CLOCK_STEP_MS = 2L

        fun watchSet(exercise: String, position: Int) = ExternalSetDomain(
            performedExerciseUuid = exercise,
            position = position,
            set = PlanSetDomain(weight = WATCH_WEIGHT, reps = WATCH_REPS, type = SetTypeDomain.WORK),
        )

        fun draft(position: Int, weight: Double, reps: Int) = LiveSetUiModel(
            position = position,
            weight = weight,
            reps = reps,
            type = SetTypeUiModel.WORK,
            isDone = false,
        )

        /** The session as the database holds it, with [committed] watch writes. */
        fun snapshot(committed: List<ExternalSetDomain> = emptyList()) = SessionSnapshotDomain(
            session = SessionDomain(
                uuid = SESSION,
                trainingUuid = TRAINING,
                state = SessionStateDomain.IN_PROGRESS,
                startedAt = 1_000L,
                finishedAt = null,
            ),
            trainingName = "Push Day",
            isAdhoc = false,
            exercises = listOf(
                exercise(PE_1, "exercise-1", 0, committed),
                exercise(PE_2, "exercise-2", 1, committed),
            ),
            preSessionPrSnapshot = emptyMap(),
        )

        fun exercise(
            uuid: String,
            exerciseUuid: String,
            position: Int,
            committed: List<ExternalSetDomain>,
        ) = LiveExerciseDomain(
            performed = PerformedExerciseDomain(
                uuid = uuid,
                sessionUuid = SESSION,
                exerciseUuid = exerciseUuid,
                position = position,
                skipped = false,
                exerciseName = "Bench press",
            ),
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            planSets = List(3) { PlanSetDomain(weight = 100.0, reps = 5, type = SetTypeDomain.WORK) },
            performedSets = committed.filter { it.performedExerciseUuid == uuid }.map { write ->
                SetDomain(
                    uuid = "set-$uuid-${write.position}",
                    weight = write.set.weight,
                    reps = write.set.reps,
                    type = write.set.type,
                    position = write.position,
                )
            },
            isPlanAttached = true,
        )
    }
}
