// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.archive.di.ArchiveHandlerStore
import io.github.stslex.workeeper.feature.archive.domain.ArchiveInteractor
import io.github.stslex.workeeper.feature.archive.domain.model.ArchivedItem
import io.github.stslex.workeeper.feature.archive.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.archive.mvi.model.ArchivedItemUi
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Event
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Segment
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.State
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ArchiveClickHandlerTest {

    private val interactor = RecordingArchiveInteractor()
    private val emptyPaging: PagingUiState<PagingData<ArchivedItemUi.Exercise>> =
        PagingUiState {
            flowOf(PagingData.empty<ArchivedItemUi.Exercise>())
        }
    private val emptyTrainingPaging: PagingUiState<PagingData<ArchivedItemUi.Training>> =
        PagingUiState {
            flowOf(PagingData.empty<ArchivedItemUi.Training>())
        }

    private val initialState = State(
        selectedSegment = Segment.EXERCISES,
        exerciseCount = 0,
        trainingCount = 0,
        exerciseSegmentLabel = "",
        trainingSegmentLabel = "",
        archivedExercisesPaging = emptyPaging,
        archivedTrainingsPaging = emptyTrainingPaging,
        pendingDeleteImpact = null,
        pendingDeleteTarget = null,
        deleteImpactLoading = false,
    )

    private val store = FakeArchiveHandlerStore(initialState)

    private val stateFlow = store.mutableState

    private val handler = ArchiveClickHandler(interactor, store)

    @Test
    fun `OnSegmentChange updates selectedSegment and emits SegmentTick haptic`() {
        handler.invoke(Action.Click.OnSegmentChange(Segment.TRAININGS))
        assertEquals(Segment.TRAININGS, stateFlow.value.selectedSegment)
        assertEquals(1, store.events.size)
        assertHaptic(store.events.single(), HapticFeedbackType.SegmentTick)
    }

    @Test
    fun `OnSegmentChange to current segment is no-op`() {
        handler.invoke(Action.Click.OnSegmentChange(Segment.EXERCISES))
        assertEquals(emptyList(), store.events)
    }

    @Test
    fun `OnRestoreClick emits ContextClick haptic`() {
        handler.invoke(Action.Click.OnRestoreClick(exerciseItem()))
        assertHaptic(store.events.first(), HapticFeedbackType.ContextClick)
    }

    @Test
    fun `OnUndoRestore emits ContextClick haptic`() {
        handler.invoke(Action.Click.OnUndoRestore(exerciseItem()))
        assertEquals(1, store.events.size)
        assertHaptic(store.events.single(), HapticFeedbackType.ContextClick)
    }

    @Test
    fun `OnDeleteDismiss does not emit haptic`() {
        stateFlow.value = stateFlow.value.copy(
            pendingDeleteTarget = exerciseItem(),
            pendingDeleteImpact = 0,
        )
        handler.invoke(Action.Click.OnDeleteDismiss)
        assertEquals(emptyList(), store.events)
    }

    @Test
    fun `OnDeleteDismiss clears pending delete state`() {
        stateFlow.value = stateFlow.value.copy(
            pendingDeleteTarget = exerciseItem(),
            pendingDeleteImpact = 5,
            deleteImpactLoading = false,
        )
        handler.invoke(Action.Click.OnDeleteDismiss)
        assertEquals(null, stateFlow.value.pendingDeleteTarget)
        assertEquals(null, stateFlow.value.pendingDeleteImpact)
    }

    @Test
    fun `OnPermanentDeleteClick emits LongPress haptic and stores target`() {
        val item = exerciseItem()
        handler.invoke(Action.Click.OnPermanentDeleteClick(item))

        assertEquals(item, stateFlow.value.pendingDeleteTarget)
        assertHaptic(store.events.first(), HapticFeedbackType.LongPress)
    }

    @Test
    fun `OnDeleteConfirm emits LongPress haptic and clears target`() {
        stateFlow.value = stateFlow.value.copy(
            pendingDeleteTarget = exerciseItem(),
            pendingDeleteImpact = 2,
        )
        handler.invoke(Action.Click.OnDeleteConfirm)
        assertHaptic(store.events.first(), HapticFeedbackType.LongPress)
        assertEquals(null, stateFlow.value.pendingDeleteTarget)
    }

    @Test
    fun `OnDeleteConfirm without target does nothing`() {
        handler.invoke(Action.Click.OnDeleteConfirm)
        assertEquals(emptyList(), interactor.exerciseDeletes)
        assertEquals(emptyList(), interactor.trainingDeletes)
    }

    private fun exerciseItem(): ArchivedItem.Exercise = ArchivedItem.Exercise(
        uuid = "uuid-x",
        name = "Bench",
        tags = emptyList(),
        archivedAt = 0L,
        type = ExerciseTypeDomain.WEIGHTED,
    )

    private fun assertHaptic(event: Event, expected: HapticFeedbackType) {
        assertTrue(event is Event.Haptic, "expected Event.Haptic but got $event")
        assertEquals(expected, (event as Event.Haptic).type)
    }
}

/**
 * The handler's store, reduced to what these identities read: state, events and the synchronous
 * State copy. Store-scope [launch] work is not run, so each identity pins only the transition and
 * the haptic it names; every other member fails fast.
 */
private class FakeArchiveHandlerStore(
    initialState: State,
) : ArchiveHandlerStore {

    val mutableState = MutableStateFlow(initialState)
    val events = mutableListOf<Event>()

    override val state: StateFlow<State> = mutableState

    override val lastAction: Action?
        get() = error("lastAction must not be read by ArchiveClickHandler")

    override val logger: Logger
        get() = error("logger must not be read by ArchiveClickHandler")

    override fun sendEvent(event: Event) {
        events += event
    }

    override fun consume(action: Action) {
        error("consume must not be used by ArchiveClickHandler")
    }

    override suspend fun consumeOnMain(action: Action): Nothing =
        error("consumeOnMain must not be used by ArchiveClickHandler")

    override fun updateState(update: (State) -> State) {
        mutableState.value = update(mutableState.value)
    }

    override suspend fun updateStateImmediate(update: suspend (State) -> State): Nothing =
        error("updateStateImmediate(update) runs only inside launched work, which is not run")

    override suspend fun updateStateImmediate(state: State): Nothing =
        error("updateStateImmediate(state) must not be used by ArchiveClickHandler")

    override fun <T> launch(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        action: suspend CoroutineScope.() -> T,
    ): Job = Job()

    override fun <T> launchDefault(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        action: suspend CoroutineScope.() -> T,
    ): Job = error("launchDefault must not be used by ArchiveClickHandler")

    override fun <T> Flow<T>.launch(
        onError: suspend (cause: Throwable) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        each: suspend (T) -> Unit,
    ): Job = error("Flow.launch must not be used by ArchiveClickHandler")
}

/** Records the two permanent deletes; the remaining suspend operations accept and do nothing. */
private class RecordingArchiveInteractor : ArchiveInteractor {

    val exerciseDeletes = mutableListOf<String>()
    val trainingDeletes = mutableListOf<String>()

    override fun observeArchivedExerciseCount(): Flow<Int> =
        error("observeArchivedExerciseCount must not be used by ArchiveClickHandler")

    override fun observeArchivedTrainingCount(): Flow<Int> =
        error("observeArchivedTrainingCount must not be used by ArchiveClickHandler")

    override fun pagedArchivedExercises(): Flow<PagingData<ArchivedItem.Exercise>> =
        error("pagedArchivedExercises must not be used by ArchiveClickHandler")

    override fun pagedArchivedTrainings(): Flow<PagingData<ArchivedItem.Training>> =
        error("pagedArchivedTrainings must not be used by ArchiveClickHandler")

    override suspend fun restoreExercise(uuid: String) = Unit

    override suspend fun restoreTraining(uuid: String) = Unit

    override suspend fun reArchiveExercise(uuid: String) = Unit

    override suspend fun reArchiveTraining(uuid: String) = Unit

    override suspend fun countExerciseSessions(uuid: String): Int = 0

    override suspend fun countTrainingSessions(uuid: String): Int = 0

    override suspend fun permanentlyDeleteExercise(uuid: String) {
        exerciseDeletes += uuid
    }

    override suspend fun permanentlyDeleteTraining(uuid: String) {
        trainingDeletes += uuid
    }
}
