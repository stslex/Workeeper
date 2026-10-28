// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStoreImpl
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.domain.model.AdhocSessionResult
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExternalSetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExternalSkipDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExternalWriteDomain
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
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.DialogState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.github.stslex.workeeper.feature.live_workout.mvi.store.PendingUndo
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * wear-live-sync.md §6.4 (D10) and §10.1, the live-workout half: the real [BaseStore] lifecycle (every
 * `init` consumes `Init`, every `dispose` ends the scope) around the real [CommonHandler], with one
 * test dispatcher for every store dispatcher. The interactor is faked: its loads can be held open,
 * and its write flow counts the collectors. CM9: no Robolectric — the store's logging is off for each
 * identity, which keeps the suite's output quiet; the previous flag is restored after it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CommonHandlerExternalWritesTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val lifetime = AppScopeLifetime()
    private val writes = MutableSharedFlow<ExternalWriteDomain>(extraBufferCapacity = WRITE_BUFFER)
    private val loads = Loads()
    private val setMutator = LiveSetMutator(StateStatusMapper())
    private val interactor = ExternalWritesInteractor(writes).apply { load = { loads.next() } }
    private val handlerStore = LiveWorkoutHandlerStoreImpl()

    /** The store's work dispatcher; one test swaps it for [ManualDispatcher] before first use. */
    private var workDispatcher: CoroutineDispatcher = dispatcher

    /** The route's state; the session-creation tests clear its session before first use. */
    private var route: State = State.create(sessionUuid = SESSION, trainingUuid = TRAINING)

    /** Every `updateStateImmediate` call the store received: a read then a write (D14). */
    private val immediateWrites = ConcurrentDeque<String>()
    private val storeDispatchers by lazy { StoreDispatchers(workDispatcher, dispatcher) }
    private val handler by lazy {
        CommonHandler(
            interactor = interactor,
            stringResolver = BlankStringResolver(),
            setMutator = setMutator,
            storeDispatchers = storeDispatchers,
            store = handlerStore,
        )
    }

    private val store by lazy {
        object : BaseStore<State, Action, Event>(
            name = "LiveWorkoutExternalWritesTest",
            initialState = route,
            storeEmitter = handlerStore,
            handlerCreator = { handler },
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

    private val owner = FakeLifecycleOwner()

    private var wasLogging = true

    @BeforeTest
    fun setUp() {
        wasLogging = Log.isLogging
        Log.isLogging = false
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        store.dispose()
        lifetime.cancel()
        Dispatchers.resetMain()
        Log.isLogging = wasLogging
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
    fun `A - an open undo window that holds the exercise gets the same patch so undo keeps the set`() {
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

    // region skips (wear-phase-2-exercise-list.md §6.5)

    @Test
    fun `A - a skip for a shown exercise sets its status and keeps its sets and drafts`() {
        open()
        emit(watchSet(PE_1, position = 0))
        store.updateState {
            it.copy(
                setDrafts = persistentMapOf(State.DraftKey(PE_1, 1) to draft(position = 1, weight = 90.0, reps = 3)),
            )
        }
        val before = store.state.value

        emit(watchSkip(PE_1, skipped = true))

        val skipped = store.state.value
        assertEquals(ExerciseStatusUiModel.SKIPPED, skipped.exercise(PE_1).status)
        assertEquals(before.exercise(PE_1).performedSets, skipped.exercise(PE_1).performedSets)
        assertEquals(before.setDrafts, skipped.setDrafts)
        assertEquals(1, loads.count, "applied in place, the session was not read again")

        emit(watchSkip(PE_1, skipped = false))

        val returned = store.state.value
        assertTrue(returned.exercise(PE_1).status != ExerciseStatusUiModel.SKIPPED, "returned to the session")
        assertEquals(before.exercise(PE_1).performedSets, returned.exercise(PE_1).performedSets)
        assertEquals(before.setDrafts, returned.setDrafts)
    }

    @Test
    fun `A - an open undo window that holds the exercise gets the same skip so undo keeps it`() {
        open()
        val before = store.state.value
        store.updateState {
            it.copy(pendingUndo = undoWindow(before, restoreExercises = before.exercises))
        }

        emit(watchSkip(PE_1, skipped = true))
        assertEquals(ExerciseStatusUiModel.SKIPPED, store.state.value.exercise(PE_1).status)
        with(handlerStore) { undoPending(interactor, setMutator, onError = {}) }

        assertNull(store.state.value.pendingUndo)
        assertEquals(ExerciseStatusUiModel.SKIPPED, store.state.value.exercise(PE_1).status)
    }

    @Test
    fun `B - a skip for a soft-deleted exercise patches only the undo window`() {
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

        emit(watchSkip(PE_2, skipped = true))

        val after = store.state.value
        assertEquals(deleted.exercises, after.exercises, "the screen does not show the deleted exercise again")
        val restored = requireNotNull(after.pendingUndo).restoreExercises
            .first { it.performedExerciseUuid == PE_2 }
        assertEquals(ExerciseStatusUiModel.SKIPPED, restored.status)
    }

    @Test
    fun `C - a skip for an exercise in neither is ignored`() {
        open()
        val before = store.state.value

        emit(watchSkip("pe-unknown", skipped = true))

        assertEquals(before, store.state.value)
    }

    @Test
    fun `a skip applied twice gives the state of the skip applied once`() {
        open()

        emit(watchSkip(PE_1, skipped = true))
        val once = store.state.value
        emit(watchSkip(PE_1, skipped = true))

        assertEquals(ExerciseStatusUiModel.SKIPPED, once.exercise(PE_1).status)
        assertEquals(once, store.state.value, "the flag is set, never toggled")
    }

    @Test
    fun `a skip changes the exercise status and leaves every other screen state as it was`() {
        open()
        store.updateState {
            it.copy(
                setDrafts = persistentMapOf(
                    State.DraftKey(PE_1, 0) to draft(position = 0, weight = 90.0, reps = 3),
                    State.DraftKey(PE_2, 0) to draft(position = 0, weight = 60.0, reps = 10),
                ),
                rowCountOverrides = persistentMapOf(PE_2 to 4),
                activeExerciseUuids = persistentSetOf(PE_2),
                expandedExerciseUuids = persistentSetOf(PE_1, PE_2),
                dialogState = DialogState.DeleteDialog(sessionName = "Push Day", progressLabel = "0/2"),
                bottomSheetState = BottomSheetState.ExerciseMenu(PE_1),
                trainingNameDraft = "Push Day B",
                isTrainingNameEditing = true,
            )
        }
        val before = store.state.value

        emit(watchSkip(PE_1, skipped = true))

        val after = store.state.value
        assertEquals(ExerciseStatusUiModel.SKIPPED, after.exercise(PE_1).status)
        assertEquals(before.exercise(PE_1).performedSets, after.exercise(PE_1).performedSets)
        assertEquals(before.setDrafts, after.setDrafts)
        assertEquals(before.rowCountOverrides, after.rowCountOverrides)
        assertEquals(before.expandedExerciseUuids, after.expandedExerciseUuids)
        assertEquals(before.dialogState, after.dialogState)
        assertEquals(before.bottomSheetState, after.bottomSheetState, "an open exercise menu stays open")
        assertEquals(before.trainingNameDraft, after.trainingNameDraft)
        assertEquals(before.isTrainingNameEditing, after.isTrainingNameEditing)
        assertEquals(before.isFinishInFlight, after.isFinishInFlight)
        assertEquals(before.isAddExerciseInFlight, after.isAddExerciseInFlight)
        assertEquals(before.pendingUndo, after.pendingUndo)
        assertEquals(1, loads.count, "no re-read of the session")
    }

    @Test
    fun `Init - a skip delivered between the load's read and its apply is shown after the load`() {
        loads.gated = true
        open()

        emit(watchSkip(PE_1, skipped = true))
        loads.release()

        assertEquals(ExerciseStatusUiModel.SKIPPED, store.state.value.exercise(PE_1).status)
    }

    @Test
    fun `processReload - a skip delivered between the load's read and its apply is shown after the load`() {
        open()
        loads.gated = true
        store.consume(Action.Common.PlanResultReceived(saved = true))
        scheduler.runCurrent()

        emit(watchSkip(PE_1, skipped = true))
        loads.release()

        assertEquals(2, loads.count)
        assertEquals(ExerciseStatusUiModel.SKIPPED, store.state.value.exercise(PE_1).status)
    }

    // endregion

    // region loads (D10)

    @Test
    fun `Init starts its load only once the subscription is active and a write between them is applied`() {
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
    fun `a return to the screen without a save then a write shows the write`() {
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
    fun `a return with a save keeps a write delivered during both loads the Init load applied last`() {
        returnWithSave(initLoadLast = true)
    }

    @Test
    fun `a return with a save keeps a write delivered during both loads the reload applied last`() {
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
    fun `Init with a plan's training starts the session subscribes before its load and shows a later write`() {
        route = State.create(sessionUuid = null, trainingUuid = TRAINING)
        interactor.startedSession = SESSION

        open()

        assertEquals(1, interactor.calls.count { it == ExternalWritesInteractor.Call("startSession", TRAINING) })
        assertCreatedSessionShowsWrites()
    }

    @Test
    fun `Init from Quick start creates the ad-hoc session subscribes before its load and shows a later write`() {
        route = State.create(sessionUuid = null, trainingUuid = null)
        interactor.adhocSession = AdhocSessionResult(sessionUuid = SESSION, trainingUuid = TRAINING)

        open()

        assertEquals(
            1,
            interactor.calls.count {
                it == ExternalWritesInteractor.Call("createAdhocSession", "", emptyList<String>())
            },
        )
        assertCreatedSessionShowsWrites()
    }

    // endregion

    // region atomic writes (D14)

    @Test
    fun `no store write reads then writes across Init a load a watch write timer ticks and a failed load`() {
        open()
        emit(watchSet(PE_1, position = 0))
        emit(watchSkip(PE_2, skipped = true))
        val shown = mutableListOf(store.state.value.nowMillis)
        repeat(TIMER_TICKS) {
            // Each tick reads the wall clock; let it move before the next one.
            awaitWallClock(WALL_CLOCK_STEP_MS)
            scheduler.advanceTimeBy(TIMER_TICK_MS)
            scheduler.runCurrent()
            shown += store.state.value.nowMillis
        }
        store.dispose()
        scheduler.runCurrent()
        interactor.load = { null }
        store.init(owner)
        scheduler.runCurrent()

        assertTrue(store.state.value.loadFailed, "the failed load set its flags")
        assertEquals(emptyList<String>(), immediateWrites.toList(), "every store write is a compare-and-set (D14)")
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
     * queue is lock-free for that dispatch.
     */
    private class ManualDispatcher : CoroutineDispatcher() {
        val queue = ConcurrentDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }
    }

    /** CM9: `Thread.sleep` is JVM-only; the wall clock is read until [millis] have passed. */
    private fun awaitWallClock(millis: Long) {
        val start = Clock.System.now()
        var now = start
        while (now - start < millis.milliseconds) now = Clock.System.now()
    }

    /** CM9: a lock-free deque on a common atomic, for the work queue and the write log. */
    @OptIn(ExperimentalAtomicApi::class)
    private class ConcurrentDeque<T> {

        private val items = AtomicReference(emptyList<T>())

        val size: Int get() = items.load().size

        operator fun plusAssign(item: T) = addLast(item)

        fun addLast(item: T) {
            while (true) {
                val current = items.load()
                if (items.compareAndSet(current, current + item)) return
            }
        }

        fun pollFirst(): T? {
            while (true) {
                val current = items.load()
                if (current.isEmpty()) return null
                if (items.compareAndSet(current, current.drop(1))) return current.first()
            }
        }

        fun removeFirst(): T = pollFirst() ?: throw NoSuchElementException("the deque is empty")

        fun toList(): List<T> = items.load()
    }

    /** CM9: the identities pin structure, not copy, so every read answers `""`. */
    private class BlankStringResolver : StringResolver {

        override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String = ""

        override suspend fun getPluralString(
            resource: PluralStringResource,
            quantity: Int,
            vararg formatArgs: Any,
        ): String = ""
    }

    /** CM9: the store's lifecycle owner, without Android's main-thread check. */
    private class FakeLifecycleOwner : LifecycleOwner {

        private val registry = LifecycleRegistry.createUnsafe(this)

        override val lifecycle: Lifecycle get() = registry
    }

    /**
     * The identities' interactor: the session's write flow, its loads as [load] answers them, and the
     * two session creations an identity sets up, each call kept in [calls]; every other member fails
     * fast.
     */
    private class ExternalWritesInteractor(
        private val writes: Flow<ExternalWriteDomain>,
    ) : LiveWorkoutInteractor {

        data class Call(val member: String, val args: List<Any?>) {
            constructor(member: String, vararg args: Any?) : this(member, args.toList())
        }

        val calls = mutableListOf<Call>()
        var load: suspend () -> SessionSnapshotDomain? = { unreached("loadSession") }
        var startedSession: String? = null
        var adhocSession: AdhocSessionResult? = null

        override suspend fun loadSession(sessionUuid: String): SessionSnapshotDomain? {
            check(sessionUuid == SESSION) { "no identity loads $sessionUuid" }
            return load()
        }

        override fun observeExternalWrites(sessionUuid: String): Flow<ExternalWriteDomain> {
            check(sessionUuid == SESSION) { "no identity observes $sessionUuid" }
            return writes
        }

        override suspend fun startSession(trainingUuid: String): String {
            calls += Call("startSession", trainingUuid)
            return startedSession?.takeIf { trainingUuid == TRAINING } ?: unreached("startSession($trainingUuid)")
        }

        override suspend fun createAdhocSession(name: String, exerciseUuids: List<String>): AdhocSessionResult {
            calls += Call("createAdhocSession", name, exerciseUuids)
            return adhocSession?.takeIf { name.isEmpty() && exerciseUuids.isEmpty() }
                ?: unreached("createAdhocSession($name, $exerciseUuids)")
        }

        override suspend fun addExerciseToActiveSession(
            sessionUuid: String,
            trainingUuid: String,
            exerciseUuid: String,
            attachToPlan: Boolean,
        ): Nothing = unreached("addExerciseToActiveSession")

        override suspend fun discardAdhocSession(sessionUuid: String, trainingUuid: String): Nothing =
            unreached("discardAdhocSession")

        override suspend fun createInlineAdhocExercise(name: String): Nothing = unreached("createInlineAdhocExercise")

        override suspend fun updateTrainingName(trainingUuid: String, name: String): Nothing =
            unreached("updateTrainingName")

        override suspend fun searchExercisesForPicker(query: String, excludedUuids: Set<String>): Nothing =
            unreached("searchExercisesForPicker")

        override suspend fun fetchPrSnapshotForExercise(exerciseUuid: String): Nothing =
            unreached("fetchPrSnapshotForExercise")

        override suspend fun upsertSet(performedExerciseUuid: String, position: Int, set: PlanSetDomain): Nothing =
            unreached("upsertSet")

        override suspend fun deleteSet(performedExerciseUuid: String, position: Int): Nothing = unreached("deleteSet")

        override suspend fun setSkipped(performedExerciseUuid: String, skipped: Boolean): Nothing =
            unreached("setSkipped")

        override suspend fun deleteExerciseFromSession(
            performedExerciseUuid: String,
            exerciseUuid: String,
            trainingUuid: String?,
            removeFromPlan: Boolean,
        ): Nothing = unreached("deleteExerciseFromSession")

        override suspend fun setPlanAttachment(
            trainingUuid: String,
            exerciseUuid: String,
            attached: Boolean,
            planSets: List<PlanSetDomain>?,
        ): Nothing = unreached("setPlanAttachment")

        override suspend fun resetExerciseSets(performedExerciseUuid: String): Nothing =
            unreached("resetExerciseSets")

        override suspend fun finishSession(sessionUuid: String, newTrainingName: String?): Nothing =
            unreached("finishSession")

        override suspend fun cancelSession(sessionUuid: String): Nothing = unreached("cancelSession")

        override suspend fun setPlanForExercise(
            trainingUuid: String,
            exerciseUuid: String,
            plan: List<PlanSetDomain>?,
        ): Nothing = unreached("setPlanForExercise")

        override suspend fun setAdhocPlan(exerciseUuid: String, plan: List<PlanSetDomain>?): Nothing =
            unreached("setAdhocPlan")

        private fun unreached(member: String): Nothing = error("no external-writes identity reaches $member")
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
    private fun emit(write: ExternalWriteDomain) {
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

    private fun State.exercise(uuid: String): LiveExerciseUiModel =
        exercises.first { it.performedExerciseUuid == uuid }

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
        val committed = mutableListOf<ExternalWriteDomain>()
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

        fun watchSkip(exercise: String, skipped: Boolean) = ExternalSkipDomain(
            performedExerciseUuid = exercise,
            skipped = skipped,
        )

        fun draft(position: Int, weight: Double, reps: Int) = LiveSetUiModel(
            position = position,
            weight = weight,
            reps = reps,
            type = SetTypeUiModel.WORK,
            isDone = false,
        )

        /** The session as the database holds it, with [committed] watch writes. */
        fun snapshot(committed: List<ExternalWriteDomain> = emptyList()) = SessionSnapshotDomain(
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
            committed: List<ExternalWriteDomain>,
        ) = LiveExerciseDomain(
            performed = PerformedExerciseDomain(
                uuid = uuid,
                sessionUuid = SESSION,
                exerciseUuid = exerciseUuid,
                position = position,
                skipped = committed.filterIsInstance<ExternalSkipDomain>()
                    .lastOrNull { it.performedExerciseUuid == uuid }
                    ?.skipped
                    ?: false,
                exerciseName = "Bench press",
            ),
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            planSets = List(3) { PlanSetDomain(weight = 100.0, reps = 5, type = SetTypeDomain.WORK) },
            performedSets = committed.filterIsInstance<ExternalSetDomain>()
                .filter { it.performedExerciseUuid == uuid }
                .map { write ->
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
