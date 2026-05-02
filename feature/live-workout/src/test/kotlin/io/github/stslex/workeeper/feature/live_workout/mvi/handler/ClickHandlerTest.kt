// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ClickHandlerTest {

    private val interactor = mockk<LiveWorkoutInteractor>(relaxed = true)
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    private val pickerHandler = mockk<ExercisePickerHandler>(relaxed = true)

    @Test
    fun `OnExerciseHeaderClick toggles expansion for DONE exercises`() {
        val stateFlow = MutableStateFlow(baseState(doneExercise(status = ExerciseStatusUiModel.DONE)))
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnExerciseHeaderClick("pe-1"))
        assertEquals(
            persistentSetOf("pe-1"),
            stateFlow.value.expandedExerciseUuids,
        )

        handler.invoke(Action.Click.OnExerciseHeaderClick("pe-1"))
        assertEquals(
            persistentSetOf<String>(),
            stateFlow.value.expandedExerciseUuids,
        )
    }

    @Test
    fun `OnExerciseHeaderClick is no-op for SKIPPED exercises`() {
        val stateFlow = MutableStateFlow(baseState(doneExercise(status = ExerciseStatusUiModel.SKIPPED)))
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnExerciseHeaderClick("pe-1"))

        assertEquals(persistentSetOf<String>(), stateFlow.value.expandedExerciseUuids)
        assertEquals(persistentSetOf<String>(), stateFlow.value.activeExerciseUuids)
    }

    @Test
    fun `OnExerciseHeaderClick on PENDING adds uuid to activeExerciseUuids and expandedExerciseUuids`() {
        val stateFlow = MutableStateFlow(
            baseState(doneExercise(status = ExerciseStatusUiModel.PENDING))
                .copy(
                    exercises = persistentListOf(
                        doneExercise(status = ExerciseStatusUiModel.CURRENT),
                        doneExercise(status = ExerciseStatusUiModel.PENDING).copy(
                            performedExerciseUuid = "pe-2",
                            exerciseUuid = "ex-2",
                            position = 1,
                        ),
                    ),
                ),
        )
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnExerciseHeaderClick("pe-2"))

        assertEquals(persistentSetOf("pe-2"), stateFlow.value.activeExerciseUuids)
        assertEquals(persistentSetOf("pe-2"), stateFlow.value.expandedExerciseUuids)
        // Status of pe-2 flips to CURRENT after recompute.
        val pe2 = stateFlow.value.exercises.first { it.performedExerciseUuid == "pe-2" }
        assertEquals(ExerciseStatusUiModel.CURRENT, pe2.status)
    }

    @Test
    fun `OnExerciseHeaderClick on auto-default CURRENT promotes to active and toggles expanded`() {
        val stateFlow = MutableStateFlow(baseState(doneExercise(status = ExerciseStatusUiModel.CURRENT)))
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnExerciseHeaderClick("pe-1"))

        assertEquals(persistentSetOf("pe-1"), stateFlow.value.activeExerciseUuids)
        assertEquals(persistentSetOf("pe-1"), stateFlow.value.expandedExerciseUuids)

        // Tapping again collapses (removes from expanded), but the uuid stays in activeUuids.
        handler.invoke(Action.Click.OnExerciseHeaderClick("pe-1"))
        assertEquals(persistentSetOf("pe-1"), stateFlow.value.activeExerciseUuids)
        assertEquals(persistentSetOf<String>(), stateFlow.value.expandedExerciseUuids)
    }

    @Test
    fun `OnDeleteSessionMenuClick flips deleteDialogVisible true`() {
        val stateFlow = MutableStateFlow(baseState(doneExercise(status = ExerciseStatusUiModel.CURRENT)))
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnDeleteSessionMenuClick)

        assertEquals(true, stateFlow.value.deleteDialogVisible)
    }

    @Test
    fun `OnDeleteSessionDismiss flips deleteDialogVisible false`() {
        val stateFlow = MutableStateFlow(
            baseState(doneExercise(status = ExerciseStatusUiModel.CURRENT))
                .copy(deleteDialogVisible = true),
        )
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnDeleteSessionDismiss)

        assertEquals(false, stateFlow.value.deleteDialogVisible)
    }

    @Test
    fun `OnDeleteSessionConfirm clears dialog without sessionUuid`() {
        val stateFlow = MutableStateFlow(
            baseState(doneExercise(status = ExerciseStatusUiModel.CURRENT))
                .copy(sessionUuid = null, deleteDialogVisible = true),
        )
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnDeleteSessionConfirm)

        assertEquals(false, stateFlow.value.deleteDialogVisible)
    }

    @Test
    fun `OnDeleteSessionConfirm with sessionUuid hides dialog`() {
        val stateFlow = MutableStateFlow(
            baseState(doneExercise(status = ExerciseStatusUiModel.CURRENT))
                .copy(deleteDialogVisible = true),
        )
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnDeleteSessionConfirm)

        assertEquals(false, stateFlow.value.deleteDialogVisible)
    }

    @Test
    fun finishSession_blankName_blocksSubmit() {
        every {
            resourceWrapper.getString(R.string.feature_live_workout_finish_name_required)
        } returns "Name is required"
        val stateFlow = MutableStateFlow(
            baseState(loggedExercise()).copy(
                trainingName = "",
                trainingNameLabel = "Untitled",
            ),
        )
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnFinishClick)
        val pending = stateFlow.value.pendingFinishConfirm
        assertTrue(pending?.requiresName == true)
        assertEquals(false, pending?.confirmEnabled)

        handler.invoke(Action.Click.OnFinishConfirm)

        assertEquals("Name is required", stateFlow.value.pendingFinishConfirm?.nameError)
        coVerify(exactly = 0) { interactor.updateTrainingName(any(), any()) }
        coVerify(exactly = 0) { interactor.finishSession(any(), any()) }
    }

    @Test
    fun finishSession_withRequiredName_collapsesToSingleAtomicCall() = runTest {
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                trainingName = "",
                trainingNameLabel = "Untitled",
                pendingFinishConfirm = State.FinishStats(
                    durationMillis = 60_000L,
                    durationLabel = "1m",
                    exercisesSummaryLabel = "1 / 1",
                    setsLoggedLabel = "1",
                    newPersonalRecords = kotlinx.collections.immutable.persistentListOf(),
                    requiresName = true,
                    nameDraft = "Push Day",
                    nameLabel = "Training name",
                    namePlaceholder = "Untitled",
                    nameError = null,
                    confirmEnabled = true,
                ),
            ),
        )
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnFinishConfirm)
        store.runLatestLaunch(this)

        // Standalone updateTrainingName must NOT fire here — the rename is now folded into
        // finishSession's transaction so a crash between the two writes is impossible.
        coVerify(exactly = 0) { interactor.updateTrainingName(any(), any()) }
        coVerify(exactly = 1) {
            interactor.finishSession(
                sessionUuid = "session-1",
                newTrainingName = "Push Day",
            )
        }
    }

    @Test
    fun onTrainingNameSubmit_persistsViaRepository() = runTest {
        val store = FakeLiveWorkoutHandlerStore(
            baseState(doneExercise(status = ExerciseStatusUiModel.CURRENT)).copy(
                trainingName = "",
                trainingNameDraft = "Push Day",
                trainingNameLabel = "Untitled",
            ),
        )
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnTrainingNameSubmit(" Push Day "))
        store.runLatestLaunch(this)

        assertEquals("Push Day", store.state.value.trainingName)
        coVerify(exactly = 1) {
            interactor.updateTrainingName("training-1", "Push Day")
        }
    }

    @Test
    fun processTrainingNameSubmit_blankInput_doesNotPersist() = runTest {
        val store = FakeLiveWorkoutHandlerStore(
            baseState(doneExercise(status = ExerciseStatusUiModel.CURRENT)).copy(
                trainingName = "Push Day",
                trainingNameDraft = "   ",
                trainingNameLabel = "Push Day",
                isTrainingNameEditing = true,
            ),
        )
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnTrainingNameSubmit("   "))
        store.runLatestLaunch(this)

        // Blank submit closes the editor but neither State nor DB carry an empty string —
        // a previously-saved name survives on next reload.
        assertEquals("Push Day", store.state.value.trainingName)
        assertEquals("Push Day", store.state.value.trainingNameLabel)
        assertEquals(false, store.state.value.isTrainingNameEditing)
        coVerify(exactly = 0) { interactor.updateTrainingName(any(), any()) }
    }

    @Test
    fun processTrainingNameSubmit_dbFailure_revertsState() = runTest {
        coEvery { interactor.updateTrainingName("training-1", "New Name") } throws
            IllegalStateException("rename failed")
        val store = FakeLiveWorkoutHandlerStore(
            baseState(doneExercise(status = ExerciseStatusUiModel.CURRENT)).copy(
                trainingName = "Old Name",
                trainingNameDraft = "Old Name",
                trainingNameLabel = "Old Name",
            ),
        )
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            store = store,
        )

        handler.invoke(Action.Click.OnTrainingNameSubmit("New Name"))
        // Optimistic update lands first.
        assertEquals("New Name", store.state.value.trainingName)
        store.runLatestLaunch(this)

        // After the interactor throws, State reverts to the pre-edit name + label so the
        // header stops lying about a value the DB never accepted.
        assertEquals("Old Name", store.state.value.trainingName)
        assertEquals("Old Name", store.state.value.trainingNameLabel)
    }

    private fun handlerStore(stateFlow: MutableStateFlow<State>): LiveWorkoutHandlerStore =
        mockk(relaxed = true) {
            every { state } returns stateFlow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
        }

    private fun baseState(exercise: LiveExerciseUiModel): State = State.create(
        sessionUuid = "session-1",
        trainingUuid = "training-1",
    ).copy(
        isLoading = false,
        exercises = persistentListOf(exercise),
    )

    private fun doneExercise(status: ExerciseStatusUiModel): LiveExerciseUiModel = LiveExerciseUiModel(
        performedExerciseUuid = "pe-1",
        exerciseUuid = "ex-1",
        exerciseName = "Bench Press",
        exerciseType = ExerciseTypeUiModel.WEIGHTED,
        position = 0,
        status = status,
        statusLabel = "",
        planSets = persistentListOf(),
        performedSets = persistentListOf(),
    )

    private fun loggedExercise(): LiveExerciseUiModel = doneExercise(
        status = ExerciseStatusUiModel.CURRENT,
    ).copy(
        performedSets = persistentListOf(
            LiveSetUiModel(
                position = 0,
                weight = 100.0,
                reps = 5,
                type = SetTypeUiModel.WORK,
                isDone = true,
            ),
        ),
    )

    private class FakeLiveWorkoutHandlerStore(
        initialState: State,
    ) : LiveWorkoutHandlerStore {

        private val stateFlow = MutableStateFlow(initialState)
        private var latestLaunch: (suspend CoroutineScope.() -> Any?)? = null
        private var latestOnError: (suspend (Throwable) -> Unit)? = null

        override val state: StateFlow<State> = stateFlow
        override val lastAction: Action? = null
        override val logger: Logger = mockk(relaxed = true)

        override fun sendEvent(event: Event) = Unit

        override fun consume(action: Action) = Unit

        override suspend fun consumeOnMain(action: Action) = Unit

        override fun updateState(update: (State) -> State) {
            stateFlow.value = update(stateFlow.value)
        }

        override suspend fun updateStateImmediate(update: suspend (State) -> State) {
            stateFlow.value = update(stateFlow.value)
        }

        override suspend fun updateStateImmediate(state: State) {
            stateFlow.value = state
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> launch(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            action: suspend CoroutineScope.() -> T,
        ): Job {
            latestLaunch = action as suspend CoroutineScope.() -> Any?
            latestOnError = onError
            return Job()
        }

        override fun <T> kotlinx.coroutines.flow.Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Job = Job()

        suspend fun runLatestLaunch(scope: CoroutineScope) {
            // Mirror production: catch a thrown action and route it through onError so
            // tests can observe the same revert/error paths the real Handler would.
            try {
                latestLaunch?.invoke(scope)
            } catch (throwable: Throwable) {
                latestOnError?.invoke(throwable)
            }
        }
    }
}
