// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveSetMutator
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.StateStatusMapper
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExternalSkipUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveWorkoutCopy
import io.github.stslex.workeeper.feature.live_workout.mvi.model.NAMED_COPY
import io.github.stslex.workeeper.feature.live_workout.mvi.store.BottomSheetState
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
    private val pickerHandler = FakeExercisePicker()
    private val statusMapper = StateStatusMapper()
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
        // Rule 4: expand -> it expands and nothing else happens anywhere.
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

        assertEquals(setOf("pe-1", "pe-2"), stateFlow.value.expandedExerciseUuids.toSet())
        assertEquals(persistentSetOf<String>(), stateFlow.value.activeExerciseUuids)
        val pe2 = stateFlow.value.exercises.first { it.performedExerciseUuid == "pe-2" }
        assertEquals(ExerciseStatusUiModel.PENDING, pe2.status)
    }

    @Test
    fun `OnExerciseHeaderClick collapse is pure — sets statuses and siblings untouched`() {
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
        // Rule 3: collapse -> it collapses, and nothing else changes.
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

        handler.invoke(Action.Click.OnSkipExercise("pe-1", skipped = true))
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

        handler.invoke(Action.Click.OnSkipExercise("pe-1", skipped = false))
        store.runLatestLaunch(this)

        assertTrue(store.state.value.exercises.first().status != ExerciseStatusUiModel.SKIPPED)
        assertEquals(1, store.state.value.exercises.first().performedSets.size)
        coVerify(exactly = 1) { interactor.setSkipped("pe-1", false) }
    }

    @Test
    fun `P14 - a watch skip that lands while the menu shows Skip exercise survives the tap and nothing is written`() =
        runTest {
            val store = FakeLiveWorkoutHandlerStore(
                baseState(loggedExercise()).copy(bottomSheetState = BottomSheetState.ExerciseMenu("pe-1")),
            )
            val handler = ClickHandler(
                interactor = interactor,
                resourceWrapper = resourceWrapper,
                pickerHandler = pickerHandler,
                setMutator = setMutator,
                store = store,
            )
            // The menu showed "Skip exercise", so its item carries that intent; the watch's skip lands first.
            val tap = Action.Click.OnSkipExercise("pe-1", skipped = true)
            store.updateState { setMutator.applyExternalWrite(it, ExternalSkipUiModel("pe-1", skipped = true)) }

            handler.invoke(tap)
            store.runLatestLaunch(this)

            assertEquals(ExerciseStatusUiModel.SKIPPED, store.state.value.exercises.first().status)
            coVerify(exactly = 0) { interactor.setSkipped(any(), any()) }
        }

    @Test
    fun `the phone's own skip and return give the state the same skip and return from the watch give`() = runTest {
        listOf(
            loggedExercise() to true,
            loggedExercise().copy(status = ExerciseStatusUiModel.SKIPPED) to false,
        ).forEach { (exercise, skipped) ->
            val initial = baseState(exercise)
            val store = FakeLiveWorkoutHandlerStore(initial)
            val handler = ClickHandler(
                interactor = interactor,
                resourceWrapper = resourceWrapper,
                pickerHandler = pickerHandler,
                setMutator = setMutator,
                store = store,
            )

            handler.invoke(Action.Click.OnSkipExercise("pe-1", skipped = skipped))

            assertEquals(
                setMutator.applyExternalWrite(initial, ExternalSkipUiModel("pe-1", skipped = skipped)),
                store.state.value,
                "skipped = $skipped",
            )
        }
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
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                liveCopy = LiveWorkoutCopy.EMPTY.copy(toastExerciseRemovedTemplate = "Removed from plan"),
            ),
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
        // Ad-hoc trainings carry real plan rows; leaving one behind trips the FK RESTRICT.
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                isAdhoc = true,
                liveCopy = LiveWorkoutCopy.EMPTY.copy(
                    toastExerciseRemovedFromWorkoutTemplate = "Removed from workout",
                ),
            ),
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
        assertEquals("New Name", store.state.value.trainingName)
        store.runLatestLaunch(this)

        // After the throw, State reverts so the header cannot show an unaccepted value.
        assertEquals("Old Name", store.state.value.trainingName)
        assertEquals("Old Name", store.state.value.trainingNameLabel)
    }

    @Test
    fun `OnAddExerciseClick opens the picker through the seam`() {
        val store = FakeLiveWorkoutHandlerStore(baseState(loggedExercise()))

        clickHandler(store).invoke(Action.Click.OnAddExerciseClick)

        assertEquals(1, pickerHandler.opens)
    }

    @Test
    fun `OnResetSets opens its confirm from the copy in one write`() {
        val store = FakeLiveWorkoutHandlerStore(baseState(loggedExercise()).copy(liveCopy = NAMED_COPY))

        clickHandler(store).invoke(Action.Click.OnResetSets("pe-1"))

        assertEquals(
            DialogState.ConfirmDialog.ResetSets(
                title = "resetTitle",
                body = "resetBody",
                confirmLabel = "resetConfirm",
                dismissLabel = "resetDismiss",
                exerciseUuid = "pe-1",
            ),
            store.state.value.dialogState,
        )
        assertEquals(1, store.updates)
        assertEquals(0, store.launches)
    }

    @Test
    fun `OnCancelSessionClick opens its confirm from the copy in one write`() {
        val store = FakeLiveWorkoutHandlerStore(baseState(loggedExercise()).copy(liveCopy = NAMED_COPY))

        clickHandler(store).invoke(Action.Click.OnCancelSessionClick)

        assertEquals(
            DialogState.ConfirmDialog.CancelSession(
                title = "cancelTitle",
                body = "cancelBody",
                confirmLabel = "cancelConfirm",
                dismissLabel = "cancelDismiss",
            ),
            store.state.value.dialogState,
        )
        assertEquals(1, store.updates)
        assertEquals(0, store.launches)
    }

    @Test
    fun `OnFinishClick on an empty session opens the empty-finish dialog from the copy in one write`() {
        val store = FakeLiveWorkoutHandlerStore(
            baseState(doneExercise(status = ExerciseStatusUiModel.CURRENT)).copy(
                isAdhoc = true,
                liveCopy = NAMED_COPY,
            ),
        )

        clickHandler(store).invoke(Action.Click.OnFinishClick)

        assertEquals(
            DialogState.EmptyFinish(
                canDiscard = true,
                confirmLabel = "emptyFinishDiscard",
                dismissLabel = "emptyFinishContinue",
            ),
            store.state.value.dialogState,
        )
        assertEquals(1, store.updates)
        assertEquals(0, store.launches)
    }

    @Test
    fun `OnDeleteSessionMenuClick fills the progress template in one write`() {
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                trainingNameLabel = "",
                doneCount = 1,
                totalCount = 2,
                setsLogged = 3,
                liveCopy = NAMED_COPY,
            ),
        )

        clickHandler(store).invoke(Action.Click.OnDeleteSessionMenuClick)

        assertEquals(
            DialogState.DeleteDialog(
                sessionName = "deleteSessionUnnamed",
                progressLabel = "deleteSessionProgressTemplate 1 2 3",
            ),
            store.state.value.dialogState,
        )
        assertEquals(1, store.updates)
        assertEquals(0, store.launches)
    }

    @Test
    fun `OnFinishClick builds the finish dialog from the copy in one write`() {
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                doneCount = 1,
                totalCount = 2,
                setsLogged = 3,
                liveCopy = NAMED_COPY,
            ),
        )

        clickHandler(store).invoke(Action.Click.OnFinishClick)

        val finish = store.state.value.dialogState as DialogState.FinishSession
        assertEquals("finishExercisesTemplate 1 2", finish.exercisesSummaryLabel)
        assertEquals("finishSetsCountTemplate 3", finish.setsLoggedLabel)
        assertEquals(listOf("finishPrWeightedTemplate 100 5"), finish.newPersonalRecords.map { it.displayLabel })
        assertEquals("finishNameLabel", finish.nameLabel)
        assertEquals("trainingNamePlaceholder", finish.namePlaceholder)
        assertEquals(1, store.updates)
        assertEquals(0, store.launches)
    }

    @Test
    fun `the set toasts read their messages from the copy`() {
        val store = FakeLiveWorkoutHandlerStore(
            statusMapper.recomputeStatuses(baseState(loggedExercise()).copy(liveCopy = NAMED_COPY)),
        )
        val handler = clickHandler(store)

        handler.invoke(Action.Click.OnAddSet("pe-1"))
        assertEquals("toastSetAdded", store.state.value.pendingUndo?.message)

        handler.invoke(Action.Click.OnRemoveLastSet("pe-1"))
        assertEquals("toastSetRemoved", store.state.value.pendingUndo?.message)
    }

    @Test
    fun `OnSetMarkDone without reps shows the copy's error without a launch`() {
        val store = FakeLiveWorkoutHandlerStore(
            baseState(doneExercise(status = ExerciseStatusUiModel.CURRENT)).copy(liveCopy = NAMED_COPY),
        )

        clickHandler(store).invoke(Action.Click.OnSetMarkDone("pe-1", position = 0))

        assertEquals(
            listOf("errorInvalidReps"),
            store.events.filterIsInstance<Event.ShowError>().map { it.message },
        )
        assertEquals(0, store.launches)
    }

    private fun clickHandler(store: LiveWorkoutHandlerStore): ClickHandler = ClickHandler(
        interactor = interactor,
        resourceWrapper = resourceWrapper,
        pickerHandler = pickerHandler,
        setMutator = setMutator,
        store = store,
    )

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

        /** D6: what a click sent, how many State writes it made and how many launches it started. */
        val events: MutableList<Event> = mutableListOf()

        var updates: Int = 0
            private set

        var launches: Int = 0
            private set

        override val state: StateFlow<State> = stateFlow
        override val lastAction: Action? = null
        override val logger: Logger = mockk(relaxed = true)

        override fun sendEvent(event: Event) {
            events += event
        }

        override fun consume(action: Action) = Unit

        override suspend fun consumeOnMain(action: Action) = Unit

        override fun updateState(update: (State) -> State) {
            updates += 1
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
            launches += 1
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
            launches += 1
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
            // Mirror production: a throw routes through onError.
            try {
                latestLaunch?.invoke(scope)
            } catch (throwable: Throwable) {
                latestOnError?.invoke(throwable)
            }
        }
    }
}
