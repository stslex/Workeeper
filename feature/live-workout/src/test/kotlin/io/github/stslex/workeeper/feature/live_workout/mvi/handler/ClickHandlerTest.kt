// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveSetMutator
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.StateStatusMapper
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.DialogState
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
import kotlinx.coroutines.flow.Flow
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
    private val statusMapper = StateStatusMapper(resourceWrapper)
    private val setMutator = LiveSetMutator(statusMapper)

    @Test
    fun `OnExerciseHeaderClick toggles expansion for DONE exercises`() {
        val stateFlow =
            MutableStateFlow(baseState(doneExercise(status = ExerciseStatusUiModel.DONE)))
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
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
    fun `OnExerciseHeaderClick toggles SKIPPED cards like any other`() {
        // Amended contract: four rules, no exceptions — the old skip no-op is retired.
        val stateFlow =
            MutableStateFlow(baseState(doneExercise(status = ExerciseStatusUiModel.SKIPPED)))
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.Click.OnExerciseHeaderClick("pe-1"))
        assertEquals(persistentSetOf("pe-1"), stateFlow.value.expandedExerciseUuids)

        handler.invoke(Action.Click.OnExerciseHeaderClick("pe-1"))
        assertEquals(persistentSetOf<String>(), stateFlow.value.expandedExerciseUuids)
    }

    @Test
    fun `OnExerciseHeaderClick expands exactly the tapped card and touches nothing else`() {
        // Amended contract rule 4: expand -> it expands, NOTHING else happens anywhere — no
        // active-set promotion, no status recompute, no sibling cards moved.
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
                    expandedExerciseUuids = persistentSetOf("pe-1"),
                ),
        )
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.Click.OnExerciseHeaderClick("pe-2"))

        // Both stay open — multiple open cards are legal and expected.
        assertEquals(setOf("pe-1", "pe-2"), stateFlow.value.expandedExerciseUuids.toSet())
        // No explicit active-set marker: the toggle leaves it untouched.
        assertEquals(persistentSetOf<String>(), stateFlow.value.activeExerciseUuids)
        val pe2 = stateFlow.value.exercises.first { it.performedExerciseUuid == "pe-2" }
        assertEquals(ExerciseStatusUiModel.PENDING, pe2.status)
    }

    @Test
    fun `OnExerciseHeaderClick collapse is pure — sets, statuses and siblings untouched`() {
        val stateFlow = MutableStateFlow(
            baseState(loggedExercise()).copy(
                expandedExerciseUuids = persistentSetOf("pe-1"),
                activeExerciseUuids = persistentSetOf("pe-1"),
            ),
        )
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.Click.OnExerciseHeaderClick("pe-1"))

        assertEquals(persistentSetOf<String>(), stateFlow.value.expandedExerciseUuids)
        // Rule 3: collapse -> it collapses, nothing else — the logged set and the (now
        // consumer-less) active marker are exactly as they were.
        assertEquals(persistentSetOf("pe-1"), stateFlow.value.activeExerciseUuids)
        assertEquals(1, stateFlow.value.exercises.first().performedSets.size)
    }

    @Test
    fun `OnDeleteSessionMenuClick shows DeleteDialog`() {
        val stateFlow =
            MutableStateFlow(baseState(doneExercise(status = ExerciseStatusUiModel.CURRENT)))
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.Click.OnDeleteSessionMenuClick)

        assertEquals(true, stateFlow.value.dialogState is DialogState.DeleteDialog)
    }

    @Test
    fun `OnFinishClick on logged session shows FinishSession dialog with requiresName when name is blank`() {
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
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.Click.OnFinishClick)

        val pending = stateFlow.value.dialogState as? DialogState.FinishSession
        assertTrue(pending?.requiresName == true)
        assertEquals(false, pending?.confirmEnabled)
    }

    @Test
    fun `OnFinishClick on empty session shows EmptyFinish dialog`() {
        val stateFlow = MutableStateFlow(
            baseState(doneExercise(status = ExerciseStatusUiModel.PENDING)).copy(
                isAdhoc = true,
            ),
        )
        val store = handlerStore(stateFlow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.Click.OnFinishClick)

        val empty = stateFlow.value.dialogState as? DialogState.EmptyFinish
        assertTrue(empty != null)
        assertEquals(true, empty?.canDiscard)
    }

    @Test
    fun `OnSkipExercise toggles to SKIPPED preserving sets and persists the flag`() = runTest {
        val store = FakeLiveWorkoutHandlerStore(baseState(loggedExercise()))
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.Click.OnSkipExercise("pe-1"))
        store.runLatestLaunch(this)

        // §6.1 / C9: no dialog, no wipe — the logged set survives the skip.
        assertEquals(ExerciseStatusUiModel.SKIPPED, store.state.value.exercises.first().status)
        assertEquals(1, store.state.value.exercises.first().performedSets.size)
        assertEquals(DialogState.Hidden, store.state.value.dialogState)
        coVerify(exactly = 1) { interactor.setSkipped("pe-1", true) }
    }

    @Test
    fun `OnSkipExercise on a skipped exercise returns it to the session`() = runTest {
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise().copy(status = ExerciseStatusUiModel.SKIPPED)),
        )
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.Click.OnSkipExercise("pe-1"))
        store.runLatestLaunch(this)

        assertTrue(store.state.value.exercises.first().status != ExerciseStatusUiModel.SKIPPED)
        assertEquals(1, store.state.value.exercises.first().performedSets.size)
        coVerify(exactly = 1) { interactor.setSkipped("pe-1", false) }
    }

    @Test
    fun `OnToggleOneOff detaches an attached exercise and persists the flip`() = runTest {
        val store = FakeLiveWorkoutHandlerStore(baseState(loggedExercise()))
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.Click.OnToggleOneOff("pe-1"))
        store.runLatestLaunch(this)

        assertEquals(false, store.state.value.exercises.first().isPlanAttached)
        coVerify(exactly = 1) {
            interactor.setPlanAttachment("training-1", "ex-1", attached = false, planSets = any())
        }
    }

    @Test
    fun `undo of a deleted exercise restores it without any DB delete`() = runTest {
        every {
            resourceWrapper.getString(
                R.string.feature_live_workout_toast_exercise_removed,
                "Bench Press",
            )
        } returns "Removed from plan"
        val store = FakeLiveWorkoutHandlerStore(baseState(loggedExercise()))
        val dialogHandler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )
        val clickHandler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        dialogHandler.invoke(Action.DialogClick.OnDeleteExerciseConfirm("pe-1"))
        // Soft removal: gone from State, pending undo armed, DB untouched (deferred).
        assertTrue(store.state.value.exercises.isEmpty())
        assertTrue(store.state.value.pendingUndo != null)
        assertEquals("Removed from plan", store.state.value.pendingUndo?.message)
        coVerify(exactly = 0) {
            interactor.deleteExerciseFromSession(any(), any(), any(), any())
        }

        clickHandler.invoke(Action.Click.OnUndoClick)

        assertEquals(1, store.state.value.exercises.size)
        assertEquals(null, store.state.value.pendingUndo)
        coVerify(exactly = 0) {
            interactor.deleteExerciseFromSession(any(), any(), any(), any())
        }
    }

    @Test
    fun `an adhoc session still removes the plan row on delete`() = runTest {
        // Ad-hoc trainings carry real plan rows, and leaving one behind made the orphan
        // cleanup trip the FK's RESTRICT and roll the whole removal back.
        every {
            resourceWrapper.getString(
                R.string.feature_live_workout_toast_exercise_removed_from_workout,
                "Bench Press",
            )
        } returns "Removed from workout"
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(isAdhoc = true),
        )
        val dialogHandler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )
        val clickHandler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        dialogHandler.invoke(Action.DialogClick.OnDeleteExerciseConfirm("pe-1"))
        assertEquals("Removed from workout", store.state.value.pendingUndo?.message)
        val undoId = store.state.value.pendingUndo?.id
        clickHandler.invoke(Action.Click.OnUndoTimeout(undoId ?: return@runTest))
        store.runLatestLaunch(this)

        coVerify(exactly = 1) {
            interactor.deleteExerciseFromSession("pe-1", "ex-1", "training-1", removeFromPlan = true)
        }
    }

    @Test
    fun `undo timeout commits the deferred exercise delete`() = runTest {
        val store = FakeLiveWorkoutHandlerStore(baseState(loggedExercise()))
        val dialogHandler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )
        val clickHandler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        dialogHandler.invoke(Action.DialogClick.OnDeleteExerciseConfirm("pe-1"))
        val undoId = store.state.value.pendingUndo?.id
        assertTrue(undoId != null)

        clickHandler.invoke(Action.Click.OnUndoTimeout(undoId ?: return@runTest))
        store.runLatestLaunch(this)

        assertEquals(null, store.state.value.pendingUndo)
        assertTrue(store.state.value.exercises.isEmpty())
        coVerify(exactly = 1) {
            interactor.deleteExerciseFromSession("pe-1", "ex-1", "training-1", removeFromPlan = true)
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
            setMutator = setMutator,
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
            setMutator = setMutator,
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
            setMutator = setMutator,
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

    private fun doneExercise(status: ExerciseStatusUiModel): LiveExerciseUiModel =
        LiveExerciseUiModel(
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

        @Suppress("UNCHECKED_CAST")
        override fun <T> launchDefault(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            action: suspend CoroutineScope.() -> T,
        ): Job {
            latestLaunch = action as suspend CoroutineScope.() -> Any?
            latestOnError = onError
            return Job()
        }

        override fun <T> Flow<T>.launch(
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
