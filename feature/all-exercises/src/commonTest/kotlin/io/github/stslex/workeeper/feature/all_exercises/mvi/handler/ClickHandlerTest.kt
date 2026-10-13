// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.feature.all_exercises.di.AllExercisesHandlerStore
import io.github.stslex.workeeper.feature.all_exercises.domain.AllExercisesInteractor
import io.github.stslex.workeeper.feature.all_exercises.domain.model.BulkArchiveResult
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import io.github.stslex.workeeper.feature.all_exercises.resources.Res
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_blocked_archive_more
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_blocked_archive_used_in_format
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_bulk_archive_success
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_permanent_delete_success
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ClickHandlerTest {

    private val interactor = RecordingAllExercisesInteractor()
    private val stringResolver = ClickHandlerStringResolver()
    private val emptyPaging = PagingUiState { flowOf(PagingData.empty<ExerciseUiModel>()) }
    private val initialState = State(
        pagingUiState = emptyPaging,
        availableTags = persistentListOf(),
        activeTagFilter = persistentSetOf(),
        pendingPermanentDelete = null,
        selectionMode = State.SelectionMode.Off,
        pendingBulkDelete = null,
        blockedArchiveDialog = null,
    )

    private val store = FakeAllExercisesHandlerStore(initialState)

    private val stateFlow = store.mutableState

    private val handler = ClickHandler(interactor, stringResolver, store)

    @Test
    fun `OnExerciseClick emits haptic and navigates to OpenDetail`() {
        handler.invoke(Action.Click.OnExerciseClick("uuid-1"))
        assertHaptic(lastSentEvent(), HapticFeedbackType.ContextClick)
        assertContains(store.consumed, Action.Navigation.OpenDetail("uuid-1"))
    }

    @Test
    fun `OnFabClick emits haptic and navigates to OpenCreate`() {
        handler.invoke(Action.Click.OnFabClick)
        assertHaptic(lastSentEvent(), HapticFeedbackType.ContextClick)
        assertContains(store.consumed, Action.Navigation.OpenCreate)
    }

    @Test
    fun `OnTagFilterToggle adds tag when not selected`() {
        handler.invoke(Action.Click.OnTagFilterToggle("tag-1"))
        assertEquals(setOf("tag-1"), stateFlow.value.activeTagFilter.toSet())
    }

    @Test
    fun `OnTagFilterToggle removes tag when already selected`() {
        stateFlow.value = stateFlow.value.copy(activeTagFilter = persistentSetOf("tag-1", "tag-2"))
        handler.invoke(Action.Click.OnTagFilterToggle("tag-1"))
        assertEquals(setOf("tag-2"), stateFlow.value.activeTagFilter.toSet())
    }

    @Test
    fun `OnCancelPermanentDelete clears pending delete`() {
        stateFlow.value = stateFlow.value.copy(
            pendingPermanentDelete = State.PendingDelete(uuid = "uuid-1", name = "Bench"),
        )
        handler.invoke(Action.Click.OnCancelPermanentDelete)
        assertEquals(null, stateFlow.value.pendingPermanentDelete)
    }

    @Test
    fun `OnConfirmPermanentDelete with no pending is no-op`() {
        handler.invoke(Action.Click.OnConfirmPermanentDelete)
        assertEquals(emptyList(), store.events)
    }

    /** The buzz after this dialog's confirm is `Confirm`, not `LongPress` (spec §26). */
    @Test
    fun `OnConfirmPermanentDelete with pending clears state and emits Confirm haptic`() {
        stateFlow.value = stateFlow.value.copy(
            pendingPermanentDelete = State.PendingDelete(uuid = "uuid-1", name = "Bench"),
        )
        handler.invoke(Action.Click.OnConfirmPermanentDelete)
        assertEquals(null, stateFlow.value.pendingPermanentDelete)
        assertTrue(store.events.any { it is Event.Haptic && it.type == HapticFeedbackType.Confirm })
    }

    /** The FAB morph fires nothing; the top bar's archive routes here too, so both are covered. */
    @Test
    fun `OnBulkDelete fires no haptic — the morph is silent`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
        )
        handler.invoke(Action.Click.OnBulkDelete)
        assertEquals(emptyList(), store.events)
    }

    /** No haptic on a filter chip: `SegmentTick` belongs to the nav bar's tab change. */
    @Test
    fun `OnTagFilterToggle fires no haptic`() {
        handler.invoke(Action.Click.OnTagFilterToggle("tag-1"))
        assertEquals(emptyList(), store.events)
    }

    /** The confirmed archive is where `Confirm` fires — after the dialog, not on the FAB. */
    @Test
    fun `OnBulkDeleteConfirm emits Confirm haptic`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
            pendingBulkDelete = State.PendingBulkDelete(count = 1),
        )
        handler.invoke(Action.Click.OnBulkDeleteConfirm)
        assertTrue(store.events.any { it is Event.Haptic && it.type == HapticFeedbackType.Confirm })
    }

    @Test
    fun `OnBulkDelete with selection opens confirm dialog`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(
                selectedUuids = persistentSetOf("uuid-1", "uuid-2"),
            ),
        )
        handler.invoke(Action.Click.OnBulkDelete)
        val pending = stateFlow.value.pendingBulkDelete
        assertNotNull(pending, "expected pendingBulkDelete to be set")
        assertEquals(2, pending!!.count)
    }

    @Test
    fun `OnBulkDeleteConfirm calls bulkArchive and clears selection on success`() = runTest {
        val targets = persistentSetOf("uuid-1", "uuid-2")
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = targets),
            pendingBulkDelete = State.PendingBulkDelete(count = 2),
        )
        interactor.bulkArchiveResult = BulkArchiveResult(archivedCount = 2, blocked = emptyList())

        handler.invoke(Action.Click.OnBulkDeleteConfirm)

        val launch = store.launches.last()
        launch.runAction(this)
        assertContains(interactor.bulkArchiveCalls, setOf("uuid-1", "uuid-2"))
        launch.runOnSuccess(this)
        assertTrue(stateFlow.value.selectionMode is State.SelectionMode.Off)
        assertNull(stateFlow.value.pendingBulkDelete)
        // Pure success surfaces a snackbar, never the blocked dialog.
        assertNull(stateFlow.value.blockedArchiveDialog)
        assertEquals(1, store.events.count { it is Event.ShowBulkDeleteSuccess })
    }

    @Test
    fun `OnBulkDeleteConfirm opens blocked dialog and no snackbar when all blocked`() = runTest {
        val targets = persistentSetOf("uuid-1")
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = targets),
            pendingBulkDelete = State.PendingBulkDelete(count = 1),
        )
        interactor.bulkArchiveResult = BulkArchiveResult(
            archivedCount = 0,
            blocked = listOf(
                BulkArchiveResult.BlockedExerciseDomain(
                    name = "Bench",
                    activeTrainings = listOf("Push", "Legs"),
                ),
            ),
        )

        handler.invoke(Action.Click.OnBulkDeleteConfirm)
        store.launches.last().run {
            runAction(this@runTest)
            runOnSuccess(this@runTest)
        }

        val dialog = stateFlow.value.blockedArchiveDialog
        assertNotNull(dialog)
        assertEquals(1, dialog?.items?.size)
        // Nothing archived → no "N archived" summary line.
        assertNull(dialog?.archivedSummary)
        assertTrue(stateFlow.value.selectionMode is State.SelectionMode.Off)
        assertNull(stateFlow.value.pendingBulkDelete)
        assertEquals(0, store.events.count { it is Event.ShowBulkDeleteSuccess })
    }

    @Test
    fun `OnBulkDeleteConfirm opens blocked dialog with archived summary on partial block`() = runTest {
        val targets = persistentSetOf("uuid-1", "uuid-2")
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = targets),
            pendingBulkDelete = State.PendingBulkDelete(count = 2),
        )
        interactor.bulkArchiveResult = BulkArchiveResult(
            archivedCount = 1,
            blocked = listOf(
                BulkArchiveResult.BlockedExerciseDomain(
                    name = "Bench",
                    activeTrainings = listOf("Push"),
                ),
            ),
        )

        handler.invoke(Action.Click.OnBulkDeleteConfirm)
        store.launches.last().run {
            runAction(this@runTest)
            runOnSuccess(this@runTest)
        }

        val dialog = stateFlow.value.blockedArchiveDialog
        assertNotNull(dialog)
        assertEquals(1, dialog?.items?.size)
        // One exercise archived → summary line present (formatted via the StringResolver fake).
        assertNotNull(dialog?.archivedSummary)
        assertEquals(0, store.events.count { it is Event.ShowBulkDeleteSuccess })
    }

    @Test
    fun `OnBlockedArchiveDismiss clears the blocked dialog`() {
        stateFlow.value = stateFlow.value.copy(
            blockedArchiveDialog = State.BlockedArchiveDialog(
                archivedSummary = null,
                items = persistentListOf(),
            ),
        )
        handler.invoke(Action.Click.OnBlockedArchiveDismiss)
        assertNull(stateFlow.value.blockedArchiveDialog)
    }

    @Test
    fun `OnBulkDeleteDismiss clears pending`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
            pendingBulkDelete = State.PendingBulkDelete(count = 1),
        )
        handler.invoke(Action.Click.OnBulkDeleteDismiss)
        assertNull(stateFlow.value.pendingBulkDelete)
    }

    /** At least one event was sent; the last one is the one a capturing slot would hold. */
    private fun lastSentEvent(): Event {
        assertTrue(store.events.isNotEmpty(), "expected at least one sendEvent")
        return store.events.last()
    }

    private fun assertHaptic(event: Event, expected: HapticFeedbackType) {
        assertTrue(event is Event.Haptic, "expected Event.Haptic but got $event")
        assertEquals(expected, (event as Event.Haptic).type)
    }
    /** The filtered-to-empty state's only action, and it is one tap rather than N. */
    @Test
    fun `OnClearTagFilter empties the whole filter in one act`() {
        stateFlow.value = stateFlow.value.copy(
            activeTagFilter = persistentSetOf("tag-1", "tag-2", "tag-3"),
        )
        handler.invoke(Action.Click.OnClearTagFilter)
        assertEquals(emptySet<String>(), stateFlow.value.activeTagFilter.toSet())
    }

    @Test
    fun `OnClearTagFilter fires no haptic`() {
        stateFlow.value = stateFlow.value.copy(activeTagFilter = persistentSetOf("tag-1"))
        handler.invoke(Action.Click.OnClearTagFilter)
        assertEquals(emptyList(), store.events)
    }

    /** Guarded, so a redundant emit cannot restart the paging flow the filter feeds. */
    @Test
    fun `OnClearTagFilter on an already-empty filter changes nothing`() {
        val before = stateFlow.value
        handler.invoke(Action.Click.OnClearTagFilter)
        assertEquals(before, stateFlow.value)
        assertEquals(0, store.updateStateCalls)
    }
    /** Entering selection is the gesture `LongPress` is for. Exactly one haptic. */
    @Test
    fun `entering selection by long press fires LongPress`() {
        handler.invoke(Action.Click.OnExerciseLongPress("uuid-1"))
        assertEquals(1, store.events.size)
        assertTrue(store.events.single().let { it is Event.Haptic && it.type == HapticFeedbackType.LongPress })
        assertTrue(stateFlow.value.selectionMode is State.SelectionMode.On)
    }

    /** A long press inside selection is a toggle: `ContextClick` and nothing else, exactly once. */
    @Test
    fun `long press inside selection fires ContextClick not a second LongPress`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
        )
        handler.invoke(Action.Click.OnExerciseLongPress("uuid-2"))
        assertEquals(1, store.events.size)
        assertEquals(
            HapticFeedbackType.ContextClick,
            (store.events.single() as Event.Haptic).type,
        )
    }

    /** Toggling what is in the selection is `ContextClick`, once. */
    @Test
    fun `selection toggle fires ContextClick`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
        )
        handler.invoke(Action.Click.OnSelectionToggle("uuid-2"))
        assertEquals(1, store.events.size)
        assertEquals(HapticFeedbackType.ContextClick, (store.events.single() as Event.Haptic).type)
    }

    /** Leaving the mode is `ContextClick` too — it changes the selection to nothing. */
    @Test
    fun `selection exit fires ContextClick`() {
        stateFlow.value = stateFlow.value.copy(
            selectionMode = State.SelectionMode.On(selectedUuids = persistentSetOf("uuid-1")),
        )
        handler.invoke(Action.Click.OnSelectionExit)
        assertEquals(1, store.events.size)
        assertEquals(HapticFeedbackType.ContextClick, (store.events.single() as Event.Haptic).type)
    }
    /**
     * The empty state's CTA opens create and fires nothing. Covers the handler only — that the
     * screen dispatches this action and not `OnFabClick` is untested screen wiring.
     */
    @Test
    fun `OnEmptyCreate opens create and fires no haptic`() {
        handler.invoke(Action.Click.OnEmptyCreate)
        assertContains(store.consumed, Action.Navigation.OpenCreate)
        assertEquals(emptyList(), store.events)
    }
}

/**
 * One Store-scope launch as the relaxed MockK store's slots held it: never run on its own; an
 * identity runs [runAction] and then [runOnSuccess], in today's order. Both closures are built
 * inside the generic `launch<T>`, so the action's result reaches onSuccess with no cast.
 */
private class RecordedLaunch(
    val runAction: suspend (CoroutineScope) -> Unit,
    val runOnSuccess: suspend (CoroutineScope) -> Unit,
)

/**
 * The handler's store as the relaxed MockK store answered it: [updateState] and the update-lambda
 * [updateStateImmediate] apply to [mutableState], [sendEvent] and [consume] are recorded, and each
 * [launch] is recorded without running. Members [ClickHandler] never reaches fail fast.
 */
private class FakeAllExercisesHandlerStore(
    initialState: State,
) : AllExercisesHandlerStore {

    val mutableState = MutableStateFlow(initialState)
    val events = mutableListOf<Event>()
    val consumed = mutableListOf<Action>()
    val launches = mutableListOf<RecordedLaunch>()

    var updateStateCalls: Int = 0
        private set

    override val state: StateFlow<State> = mutableState

    override val lastAction: Action?
        get() = error("lastAction must not be read by ClickHandler")

    override val logger: Logger
        get() = error("logger must not be read by ClickHandler")

    override fun sendEvent(event: Event) {
        events.add(event)
    }

    override fun consume(action: Action) {
        consumed.add(action)
    }

    override suspend fun consumeOnMain(action: Action): Nothing =
        error("consumeOnMain must not be used by ClickHandler")

    override fun updateState(update: (State) -> State) {
        updateStateCalls += 1
        mutableState.value = update(mutableState.value)
    }

    override suspend fun updateStateImmediate(update: suspend (State) -> State) {
        mutableState.value = update(mutableState.value)
    }

    override suspend fun updateStateImmediate(state: State): Nothing =
        error("updateStateImmediate(state) must not be used by ClickHandler")

    override fun <T> launch(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        action: suspend CoroutineScope.() -> T,
    ): Job {
        val results = mutableListOf<T>()
        launches.add(
            RecordedLaunch(
                runAction = { scope -> results.add(scope.action()) },
                runOnSuccess = { scope -> scope.onSuccess(results.single()) },
            ),
        )
        return Job()
    }

    override fun <T> launchDefault(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        action: suspend CoroutineScope.() -> T,
    ): Job = error("launchDefault must not be used by ClickHandler")

    override fun <T> Flow<T>.launch(
        onError: suspend (cause: Throwable) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        each: suspend (T) -> Unit,
    ): Job = error("Flow.launch must not be used by ClickHandler")
}

/** Records [bulkArchive] and answers it with [bulkArchiveResult]; every other member fails fast. */
private class RecordingAllExercisesInteractor : AllExercisesInteractor {

    val bulkArchiveCalls = mutableListOf<Set<String>>()
    var bulkArchiveResult = BulkArchiveResult(archivedCount = 0, blocked = emptyList())

    override suspend fun bulkArchive(uuids: Set<String>): BulkArchiveResult {
        bulkArchiveCalls.add(uuids)
        return bulkArchiveResult
    }

    override fun observeExercises(filterTagUuids: Set<String>): Nothing =
        error("observeExercises must not be used by ClickHandler")

    override fun observeAvailableTags(): Nothing =
        error("observeAvailableTags must not be used by ClickHandler")

    override suspend fun archiveExercise(uuid: String): Nothing =
        error("archiveExercise must not be used by ClickHandler")

    override suspend fun restoreExercise(uuid: String): Nothing =
        error("restoreExercise must not be used by ClickHandler")

    override suspend fun canPermanentlyDelete(uuid: String): Nothing =
        error("canPermanentlyDelete must not be used by ClickHandler")

    override suspend fun permanentlyDelete(uuid: String): Nothing =
        error("permanentlyDelete is only reached by a launch no identity runs")

    override suspend fun getExercise(uuid: String): Nothing =
        error("getExercise must not be used by ClickHandler")

    override suspend fun countSessionsForExercise(uuid: String): Nothing =
        error("countSessionsForExercise must not be used by ClickHandler")

    override fun observeLinkedTrainingsCount(uuid: String): Nothing =
        error("observeLinkedTrainingsCount must not be used by ClickHandler")

    override fun observeLastTrainedAt(uuid: String): Nothing =
        error("observeLastTrainedAt must not be used by ClickHandler")

    override suspend fun bulkPermanentDelete(uuids: Set<String>): Nothing =
        error("bulkPermanentDelete must not be used by ClickHandler")

    override suspend fun canBulkPermanentDelete(uuids: Set<String>): Nothing =
        error("canBulkPermanentDelete must not be used by ClickHandler")
}

/**
 * The strict resolver: the relaxed mock's `""` for the four resources [ClickHandler] reads, and
 * only with the arguments production passes. Any other resource or argument shape throws.
 */
private class ClickHandlerStringResolver : StringResolver {

    override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String =
        when (resource) {
            Res.string.feature_all_exercises_permanent_delete_success -> {
                check(formatArgs.isEmpty()) { "permanent_delete_success takes no arguments" }
                ""
            }

            Res.string.feature_all_exercises_blocked_archive_used_in_format -> {
                check(formatArgs.size == 1) { "used_in_format takes exactly one argument" }
                ""
            }

            else -> error("ClickHandler must not read $resource")
        }

    override suspend fun getPluralString(
        resource: PluralStringResource,
        quantity: Int,
        vararg formatArgs: Any,
    ): String {
        check(formatArgs.toList() == listOf(quantity)) {
            "a plural read passes its quantity as its one format argument; got ${formatArgs.toList()}"
        }
        return when (resource) {
            Res.plurals.feature_all_exercises_bulk_archive_success -> ""
            Res.plurals.feature_all_exercises_blocked_archive_more -> ""
            else -> error("ClickHandler must not read $resource")
        }
    }
}
