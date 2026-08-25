// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.domain.model.FinishResult
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
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
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

internal class DialogClickHandlerTest {

    private val interactor = mockk<LiveWorkoutInteractor>(relaxed = true)
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    private val pickerHandler = mockk<ExercisePickerHandler>(relaxed = true)
    private val statusMapper = StateStatusMapper(resourceWrapper)
    private val setMutator = LiveSetMutator(statusMapper)

    // region Dismiss actions — every dismiss flips DialogState to Hidden

    @Test
    fun `OnDeleteSessionDismiss hides the dialog`() {
        val stateFlow = MutableStateFlow(
            baseState().copy(
                dialogState = DialogState.DeleteDialog(
                    sessionName = "session name",
                    progressLabel = "Progress label",
                ),
            ),
        )
        handler(stateFlow).invoke(Action.DialogClick.OnDeleteSessionDismiss)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    @Test
    fun `OnCancelSessionDismiss hides the dialog`() {
        val stateFlow = MutableStateFlow(
            baseState().copy(
                dialogState = cancelDialog(),
            ),
        )
        handler(stateFlow).invoke(Action.DialogClick.OnCancelSessionDismiss)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    @Test
    fun `OnResetSetsDismiss hides the dialog`() {
        val stateFlow = MutableStateFlow(
            baseState().copy(
                dialogState = DialogState.ConfirmDialog.ResetSets(
                    title = "title",
                    body = "body",
                    confirmLabel = "confirm",
                    dismissLabel = "dismiss",
                    exerciseUuid = "pe-1",
                ),
            ),
        )
        handler(stateFlow).invoke(Action.DialogClick.OnResetSetsDismiss)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    @Test
    fun `OnFinishDismiss hides the dialog`() {
        val stateFlow = MutableStateFlow(
            baseState().copy(dialogState = finishDialog(requiresName = false)),
        )
        handler(stateFlow).invoke(Action.DialogClick.OnFinishDismiss)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    // endregion

    // region OnDeleteSessionConfirm

    @Test
    fun `OnDeleteSessionConfirm without sessionUuid hides the dialog`() {
        val stateFlow = MutableStateFlow(
            baseState().copy(
                sessionUuid = null,
                dialogState = DialogState.DeleteDialog("session", "progress"),
            ),
        )
        handler(stateFlow).invoke(Action.DialogClick.OnDeleteSessionConfirm)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        coVerify(exactly = 0) { interactor.cancelSession(any()) }
    }

    @Test
    fun `OnDeleteSessionConfirm with sessionUuid hides dialog and calls cancelSession`() = runTest {
        val store = FakeLiveWorkoutHandlerStore(
            baseState().copy(
                dialogState = DialogState.DeleteDialog("session", "progress"),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnDeleteSessionConfirm)
        store.runLatestLaunch(this)

        assertEquals(DialogState.Hidden, store.state.value.dialogState)
        coVerify(exactly = 1) { interactor.cancelSession("session-1") }
        assertEquals(listOf<Action>(Action.Navigation.Back), store.consumedOnMain)
    }

    @Test
    fun `OnDeleteSessionConfirm failure surfaces CancelFailed error`() = runTest {
        coEvery { interactor.cancelSession("session-1") } throws IllegalStateException("boom")
        every {
            resourceWrapper.getString(R.string.feature_live_workout_error_cancel_failed)
        } returns "Cancel failed"
        val store = FakeLiveWorkoutHandlerStore(
            baseState().copy(
                dialogState = DialogState.DeleteDialog("session", "progress"),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnDeleteSessionConfirm)
        store.runLatestLaunch(this)

        assertTrue(
            store.events.any { it is Event.ShowError && it.message == "Cancel failed" },
        )
    }

    // endregion

    // region OnCancelSessionConfirm

    @Test
    fun `OnCancelSessionConfirm without sessionUuid only navigates back`() {
        val store = FakeLiveWorkoutHandlerStore(
            baseState().copy(
                sessionUuid = null,
                dialogState = cancelDialog(),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnCancelSessionConfirm)

        assertEquals(listOf<Action>(Action.Navigation.Back), store.consumed)
        coVerify(exactly = 0) { interactor.cancelSession(any()) }
    }

    @Test
    fun `OnCancelSessionConfirm calls cancelSession and navigates back`() = runTest {
        val store = FakeLiveWorkoutHandlerStore(
            baseState().copy(dialogState = cancelDialog()),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnCancelSessionConfirm)
        store.runLatestLaunch(this)

        coVerify(exactly = 1) { interactor.cancelSession("session-1") }
        assertEquals(listOf<Action>(Action.Navigation.Back), store.consumedOnMain)
    }

    @Test
    fun `OnCancelSessionConfirm failure surfaces CancelFailed error`() = runTest {
        coEvery { interactor.cancelSession("session-1") } throws IllegalStateException("boom")
        every {
            resourceWrapper.getString(R.string.feature_live_workout_error_cancel_failed)
        } returns "Cancel failed"
        val store = FakeLiveWorkoutHandlerStore(
            baseState().copy(dialogState = cancelDialog()),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnCancelSessionConfirm)
        store.runLatestLaunch(this)

        assertTrue(
            store.events.any { it is Event.ShowError && it.message == "Cancel failed" },
        )
    }

    // endregion

    // region OnEmptyFinishContinue / OnEmptyFinishDiscard

    @Test
    fun `OnEmptyFinishContinue hides the dialog`() {
        val stateFlow = MutableStateFlow(
            baseState().copy(
                dialogState = DialogState.EmptyFinish(
                    canDiscard = true,
                    confirmLabel = "Discard",
                    dismissLabel = "Continue",
                ),
            ),
        )

        handler(stateFlow).invoke(Action.DialogClick.OnEmptyFinishContinue)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    @Test
    fun `OnEmptyFinishDiscard for library training only hides the dialog`() {
        val stateFlow = MutableStateFlow(
            baseState().copy(
                isAdhoc = false,
                dialogState = DialogState.EmptyFinish(
                    canDiscard = false,
                    confirmLabel = "Discard",
                    dismissLabel = "Continue",
                ),
            ),
        )
        handler(stateFlow).invoke(Action.DialogClick.OnEmptyFinishDiscard)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        coVerify(exactly = 0) { interactor.discardAdhocSession(any(), any()) }
    }

    @Test
    fun `OnEmptyFinishDiscard with adhoc training discards and navigates back`() = runTest {
        val store = FakeLiveWorkoutHandlerStore(
            baseState().copy(
                isAdhoc = true,
                dialogState = DialogState.EmptyFinish(
                    canDiscard = true,
                    confirmLabel = "Discard",
                    dismissLabel = "Continue",
                ),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnEmptyFinishDiscard)

        assertEquals(DialogState.Hidden, store.state.value.dialogState)
        assertEquals(true, store.state.value.isFinishInFlight)
        store.runLatestLaunch(this)

        coVerify(exactly = 1) {
            interactor.discardAdhocSession(
                sessionUuid = "session-1",
                trainingUuid = "training-1",
            )
        }
        assertEquals(listOf<Action>(Action.Navigation.Back), store.consumedOnMain)
    }

    @Test
    fun `OnEmptyFinishDiscard with blank sessionUuid only hides the dialog`() = runTest {
        val store = FakeLiveWorkoutHandlerStore(
            baseState().copy(
                isAdhoc = true,
                sessionUuid = "",
                dialogState = DialogState.EmptyFinish(
                    canDiscard = true,
                    confirmLabel = "Discard",
                    dismissLabel = "Continue",
                ),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnEmptyFinishDiscard)

        assertEquals(DialogState.Hidden, store.state.value.dialogState)
        assertEquals(false, store.state.value.isFinishInFlight)
        coVerify(exactly = 0) { interactor.discardAdhocSession(any(), any()) }
    }

    @Test
    fun `OnEmptyFinishDiscard failure clears the in-flight flag and surfaces error`() = runTest {
        coEvery {
            interactor.discardAdhocSession(any(), any())
        } throws IllegalStateException("boom")
        every {
            resourceWrapper.getString(R.string.feature_live_workout_error_discard_session_failed)
        } returns "Discard failed"
        val store = FakeLiveWorkoutHandlerStore(
            baseState().copy(
                isAdhoc = true,
                dialogState = DialogState.EmptyFinish(
                    canDiscard = true,
                    confirmLabel = "Discard",
                    dismissLabel = "Continue",
                ),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnEmptyFinishDiscard)
        store.runLatestLaunch(this)

        assertEquals(false, store.state.value.isFinishInFlight)
        assertTrue(
            store.events.any { it is Event.ShowError && it.message == "Discard failed" },
        )
    }

    // endregion

    // region OnResetSetsConfirm

    @Test
    fun `OnResetSetsConfirm clears performed sets and calls resetExerciseSets`() = runTest {
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                dialogState = DialogState.ConfirmDialog.ResetSets(
                    title = "title",
                    body = "body",
                    confirmLabel = "confirm",
                    dismissLabel = "dismiss",
                    exerciseUuid = "pe-1",
                ),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnResetSetsConfirm("pe-1"))
        store.runLatestLaunch(this)

        assertTrue(store.state.value.exercises.first().performedSets.isEmpty())
        assertEquals(DialogState.Hidden, store.state.value.dialogState)
        coVerify(exactly = 1) { interactor.resetExerciseSets("pe-1") }
    }

    @Test
    fun `OnResetSetsConfirm failure surfaces ResetFailed error`() = runTest {
        coEvery { interactor.resetExerciseSets(any()) } throws IllegalStateException("boom")
        every {
            resourceWrapper.getString(R.string.feature_live_workout_error_reset_failed)
        } returns "Reset failed"
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                dialogState = DialogState.ConfirmDialog.ResetSets(
                    title = "title",
                    body = "body",
                    confirmLabel = "confirm",
                    dismissLabel = "dismiss",
                    exerciseUuid = "pe-1",
                ),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnResetSetsConfirm("pe-1"))
        store.runLatestLaunch(this)

        assertTrue(
            store.events.any { it is Event.ShowError && it.message == "Reset failed" },
        )
    }

    // endregion

    // region OnFinishConfirm

    @Test
    fun `OnFinishConfirm without sessionUuid is a no-op`() = runTest {
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                sessionUuid = null,
                dialogState = finishDialog(requiresName = false),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnFinishConfirm)

        coVerify(exactly = 0) { interactor.finishSession(any(), any()) }
    }

    @Test
    fun `OnFinishConfirm with blank required name sets nameError and skips finishSession`() = runTest {
        every {
            resourceWrapper.getString(R.string.feature_live_workout_finish_name_required)
        } returns "Name is required"
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                trainingName = "",
                trainingNameLabel = "Untitled",
                dialogState = finishDialog(requiresName = true, nameDraft = ""),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnFinishConfirm)

        val finish = store.state.value.dialogState as? DialogState.FinishSession
        assertEquals("Name is required", finish?.nameError)
        assertEquals(false, finish?.confirmEnabled)
        coVerify(exactly = 0) { interactor.finishSession(any(), any()) }
        coVerify(exactly = 0) { interactor.updateTrainingName(any(), any()) }
    }

    @Test
    fun `OnFinishConfirm with valid name calls finishSession atomically`() = runTest {
        coEvery {
            interactor.finishSession(sessionUuid = "session-1", newTrainingName = "Push Day")
        } returns finishResult()
        every {
            resourceWrapper.getString(R.string.feature_live_workout_finish_success)
        } returns "Saved"
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                trainingName = "",
                trainingNameLabel = "Untitled",
                dialogState = finishDialog(requiresName = true, nameDraft = "Push Day"),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnFinishConfirm)
        store.runLatestLaunch(this)

        // The rename is folded into finishSession's transaction, so it must not fire alone.
        coVerify(exactly = 0) { interactor.updateTrainingName(any(), any()) }
        coVerify(exactly = 1) {
            interactor.finishSession(
                sessionUuid = "session-1",
                newTrainingName = "Push Day",
            )
        }
        assertTrue(
            store.events.any {
                it is Event.ShowSessionSavedSnackbar && it.message == "Saved"
            },
        )
        assertEquals(
            listOf<Action>(Action.Navigation.OpenPastSession(sessionUuid = "session-1")),
            store.consumedOnMain,
        )
    }

    @Test
    fun `OnFinishConfirm without required name reuses existing trainingName`() = runTest {
        coEvery {
            interactor.finishSession(sessionUuid = "session-1", newTrainingName = null)
        } returns finishResult()
        every {
            resourceWrapper.getString(R.string.feature_live_workout_finish_success)
        } returns "Saved"
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                trainingName = "Push Day",
                trainingNameLabel = "Push Day",
                dialogState = finishDialog(requiresName = false, nameDraft = "Push Day"),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnFinishConfirm)
        store.runLatestLaunch(this)

        coVerify(exactly = 1) {
            interactor.finishSession(
                sessionUuid = "session-1",
                newTrainingName = null,
            )
        }
    }

    @Test
    fun `OnFinishConfirm with required name and missing trainingUuid surfaces FinishFailed`() = runTest {
        every {
            resourceWrapper.getString(R.string.feature_live_workout_error_finish_failed)
        } returns "Finish failed"
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                trainingUuid = null,
                trainingName = "",
                dialogState = finishDialog(requiresName = true, nameDraft = "Push Day"),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnFinishConfirm)

        coVerify(exactly = 0) { interactor.finishSession(any(), any()) }
        assertTrue(
            store.events.any { it is Event.ShowError && it.message == "Finish failed" },
        )
    }

    @Test
    fun `OnFinishConfirm null result surfaces FinishMissingSession`() = runTest {
        coEvery {
            interactor.finishSession(any(), any())
        } returns null
        every {
            resourceWrapper.getString(R.string.feature_live_workout_error_finish_missing_session)
        } returns "Missing session"
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                trainingName = "Push Day",
                dialogState = finishDialog(requiresName = false, nameDraft = "Push Day"),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnFinishConfirm)
        store.runLatestLaunch(this)

        assertEquals(false, store.state.value.isFinishInFlight)
        assertTrue(
            store.events.any { it is Event.ShowError && it.message == "Missing session" },
        )
    }

    @Test
    fun `OnFinishConfirm failure clears the in-flight flag and surfaces FinishFailed`() = runTest {
        coEvery {
            interactor.finishSession(any(), any())
        } throws IllegalStateException("boom")
        every {
            resourceWrapper.getString(R.string.feature_live_workout_error_finish_failed)
        } returns "Finish failed"
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                trainingName = "Push Day",
                dialogState = finishDialog(requiresName = false, nameDraft = "Push Day"),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnFinishConfirm)
        store.runLatestLaunch(this)

        assertEquals(false, store.state.value.isFinishInFlight)
        assertTrue(
            store.events.any { it is Event.ShowError && it.message == "Finish failed" },
        )
    }

    // endregion

    // region OnFinishNameChange

    @Test
    fun `OnFinishNameChange with non-blank text updates draft and clears error`() {
        val stateFlow = MutableStateFlow(
            baseState(loggedExercise()).copy(
                dialogState = finishDialog(
                    requiresName = true,
                    nameDraft = "",
                    nameError = "Name is required",
                    confirmEnabled = false,
                ),
            ),
        )

        handler(stateFlow).invoke(Action.DialogClick.OnFinishNameChange("Push Day"))

        val finish = stateFlow.value.dialogState as DialogState.FinishSession
        assertEquals("Push Day", finish.nameDraft)
        assertEquals(null, finish.nameError)
        assertEquals(true, finish.confirmEnabled)
    }

    @Test
    fun `OnFinishNameChange with blank text sets the required error and disables confirm`() {
        every {
            resourceWrapper.getString(R.string.feature_live_workout_finish_name_required)
        } returns "Name is required"
        val stateFlow = MutableStateFlow(
            baseState(loggedExercise()).copy(
                dialogState = finishDialog(
                    requiresName = true,
                    nameDraft = "Push Day",
                    nameError = null,
                    confirmEnabled = true,
                ),
            ),
        )

        handler(stateFlow).invoke(Action.DialogClick.OnFinishNameChange("   "))

        val finish = stateFlow.value.dialogState as DialogState.FinishSession
        assertEquals("   ", finish.nameDraft)
        assertEquals("Name is required", finish.nameError)
        assertEquals(false, finish.confirmEnabled)
    }

    @Test
    fun `OnFinishNameChange is a no-op when dialog is not FinishSession`() {
        val stateFlow = MutableStateFlow(
            baseState(loggedExercise()).copy(
                dialogState = DialogState.Hidden,
            ),
        )

        handler(stateFlow).invoke(Action.DialogClick.OnFinishNameChange("Push Day"))

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    // endregion

    // region PickerAction delegation

    @Test
    fun `PickerAction delegates to the picker sub-handler`() {
        val stateFlow = MutableStateFlow(baseState(loggedExercise()))
        val picker = ExercisePickerAction.OnDismiss

        handler(stateFlow).invoke(Action.DialogClick.PickerAction(picker))

        verify(exactly = 1) { pickerHandler.invoke(picker) }
    }

    // endregion

    // region helpers

    private fun handler(stateFlow: MutableStateFlow<State>): DialogClickHandler =
        DialogClickHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = handlerStore(stateFlow),
        )

    private fun handlerStore(stateFlow: MutableStateFlow<State>): LiveWorkoutHandlerStore =
        mockk(relaxed = true) {
            every { state } returns stateFlow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
        }

    private fun baseState(
        exercise: LiveExerciseUiModel = doneExercise(ExerciseStatusUiModel.CURRENT),
    ): State = State.create(
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

    private fun finishResult(): FinishResult = FinishResult(
        durationMillis = 60_000L,
        doneCount = 1,
        totalCount = 1,
        skippedCount = 0,
        setsLogged = 1,
    )

    private fun cancelDialog(): DialogState.ConfirmDialog.CancelSession =
        DialogState.ConfirmDialog.CancelSession(
            title = "title",
            body = "body",
            confirmLabel = "confirm",
            dismissLabel = "dismiss",
        )

    private fun finishDialog(
        requiresName: Boolean,
        nameDraft: String = "",
        nameError: String? = null,
        confirmEnabled: Boolean = !requiresName,
    ): DialogState.FinishSession = DialogState.FinishSession(
        durationMillis = 60_000L,
        durationLabel = "1m",
        exercisesSummaryLabel = "1 / 1",
        setsLoggedLabel = "1",
        newPersonalRecords = persistentListOf(),
        requiresName = requiresName,
        nameDraft = nameDraft,
        nameLabel = "Training name",
        namePlaceholder = "Untitled",
        nameError = nameError,
        confirmEnabled = confirmEnabled,
    )

    /** Test double capturing the latest [launch] block and the `consume` invocations. */
    private class FakeLiveWorkoutHandlerStore(
        initialState: State,
    ) : LiveWorkoutHandlerStore {

        private val stateFlow = MutableStateFlow(initialState)
        private var latestLaunch: (suspend CoroutineScope.() -> Any?)? = null
        private var latestOnError: (suspend (Throwable) -> Unit)? = null
        private var latestOnSuccess: (suspend CoroutineScope.(Any?) -> Unit)? = null

        val consumed: MutableList<Action> = mutableListOf()
        val consumedOnMain: MutableList<Action> = mutableListOf()
        val events: MutableList<Event> = mutableListOf()

        override val state: StateFlow<State> = stateFlow
        override val lastAction: Action? = null
        override val logger: Logger = mockk(relaxed = true)

        override fun sendEvent(event: Event) {
            events.add(event)
        }

        override fun consume(action: Action) {
            consumed.add(action)
        }

        override suspend fun consumeOnMain(action: Action) {
            consumedOnMain.add(action)
        }

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
            latestOnSuccess = onSuccess as suspend CoroutineScope.(Any?) -> Unit
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
            latestOnSuccess = onSuccess as suspend CoroutineScope.(Any?) -> Unit
            return Job()
        }

        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Job = Job()

        suspend fun runLatestLaunch(scope: CoroutineScope) {
            // Mirror production: a throw routes through onError, a result through onSuccess.
            try {
                val result = latestLaunch?.invoke(scope)
                latestOnSuccess?.invoke(scope, result)
            } catch (throwable: Throwable) {
                latestOnError?.invoke(throwable)
            }
        }
    }

    // endregion
}
