// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.domain.model.AddExerciseResult
import io.github.stslex.workeeper.feature.live_workout.domain.model.AdhocSessionResult
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExercisePickerEntry
import io.github.stslex.workeeper.feature.live_workout.domain.model.FinishResult
import io.github.stslex.workeeper.feature.live_workout.domain.model.InlineAdhocResult
import io.github.stslex.workeeper.feature.live_workout.domain.model.PersonalRecordDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionSnapshotDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveSetMutator
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.StateStatusMapper
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveWorkoutCopy
import io.github.stslex.workeeper.feature.live_workout.mvi.model.NAMED_COPY
import io.github.stslex.workeeper.feature.live_workout.mvi.store.DialogState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.github.stslex.workeeper.feature.live_workout.resources.Res
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_cancel_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_discard_session_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_finish_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_finish_missing_session
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_reset_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_success
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class DialogClickHandlerTest {

    private val interactor = RecordingInteractor()
    private val stringResolver = RelaxedStringResolver()
    private val pickerHandler = FakeExercisePicker()
    private val statusMapper = StateStatusMapper()
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
        assertEquals(0, interactor.count("cancelSession", RecordingInteractor.ANY))
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
            stringResolver = stringResolver,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnDeleteSessionConfirm)
        store.runLatestLaunch(this)

        assertEquals(DialogState.Hidden, store.state.value.dialogState)
        assertEquals(1, interactor.count("cancelSession", "session-1"))
        assertEquals(listOf<Action>(Action.Navigation.Back), store.consumedOnMain)
    }

    @Test
    fun `OnDeleteSessionConfirm failure surfaces CancelFailed error`() = runTest {
        interactor.onCall("cancelSession", "session-1") { throw IllegalStateException("boom") }
        stringResolver.answer(Res.string.feature_live_workout_error_cancel_failed, value = "Cancel failed")
        val store = FakeLiveWorkoutHandlerStore(
            baseState().copy(
                dialogState = DialogState.DeleteDialog("session", "progress"),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            stringResolver = stringResolver,
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
            stringResolver = stringResolver,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnCancelSessionConfirm)

        assertEquals(listOf<Action>(Action.Navigation.Back), store.consumed)
        assertEquals(0, interactor.count("cancelSession", RecordingInteractor.ANY))
    }

    @Test
    fun `OnCancelSessionConfirm calls cancelSession and navigates back`() = runTest {
        val store = FakeLiveWorkoutHandlerStore(
            baseState().copy(dialogState = cancelDialog()),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            stringResolver = stringResolver,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnCancelSessionConfirm)
        store.runLatestLaunch(this)

        assertEquals(1, interactor.count("cancelSession", "session-1"))
        assertEquals(listOf<Action>(Action.Navigation.Back), store.consumedOnMain)
    }

    @Test
    fun `OnCancelSessionConfirm failure surfaces CancelFailed error`() = runTest {
        interactor.onCall("cancelSession", "session-1") { throw IllegalStateException("boom") }
        stringResolver.answer(Res.string.feature_live_workout_error_cancel_failed, value = "Cancel failed")
        val store = FakeLiveWorkoutHandlerStore(
            baseState().copy(dialogState = cancelDialog()),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            stringResolver = stringResolver,
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
        assertEquals(0, interactor.count("discardAdhocSession", RecordingInteractor.ANY, RecordingInteractor.ANY))
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
            stringResolver = stringResolver,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnEmptyFinishDiscard)

        assertEquals(DialogState.Hidden, store.state.value.dialogState)
        assertEquals(true, store.state.value.isFinishInFlight)
        store.runLatestLaunch(this)

        assertEquals(1, interactor.count("discardAdhocSession", "session-1", "training-1"))
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
            stringResolver = stringResolver,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnEmptyFinishDiscard)

        assertEquals(DialogState.Hidden, store.state.value.dialogState)
        assertEquals(false, store.state.value.isFinishInFlight)
        assertEquals(0, interactor.count("discardAdhocSession", RecordingInteractor.ANY, RecordingInteractor.ANY))
    }

    @Test
    fun `OnEmptyFinishDiscard failure clears the in-flight flag and surfaces error`() = runTest {
        interactor.onCall("discardAdhocSession", RecordingInteractor.ANY, RecordingInteractor.ANY) {
            throw IllegalStateException("boom")
        }
        stringResolver.answer(Res.string.feature_live_workout_error_discard_session_failed, value = "Discard failed")
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
            stringResolver = stringResolver,
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
            stringResolver = stringResolver,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnResetSetsConfirm("pe-1"))
        store.runLatestLaunch(this)

        assertTrue(store.state.value.exercises.first().performedSets.isEmpty())
        assertEquals(DialogState.Hidden, store.state.value.dialogState)
        assertEquals(1, interactor.count("resetExerciseSets", "pe-1"))
    }

    @Test
    fun `OnResetSetsConfirm failure surfaces ResetFailed error`() = runTest {
        interactor.onCall("resetExerciseSets", RecordingInteractor.ANY) { throw IllegalStateException("boom") }
        stringResolver.answer(Res.string.feature_live_workout_error_reset_failed, value = "Reset failed")
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
            stringResolver = stringResolver,
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
            stringResolver = stringResolver,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnFinishConfirm)

        assertEquals(0, interactor.count("finishSession", RecordingInteractor.ANY, RecordingInteractor.ANY))
    }

    @Test
    fun `OnFinishConfirm with blank required name sets nameError and skips finishSession`() = runTest {
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                trainingName = "",
                trainingNameLabel = "Untitled",
                dialogState = finishDialog(requiresName = true, nameDraft = ""),
                liveCopy = LiveWorkoutCopy.EMPTY.copy(finishNameRequired = "Name is required"),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            stringResolver = stringResolver,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnFinishConfirm)

        val finish = store.state.value.dialogState as? DialogState.FinishSession
        assertEquals("Name is required", finish?.nameError)
        assertEquals(false, finish?.confirmEnabled)
        assertEquals(0, interactor.count("finishSession", RecordingInteractor.ANY, RecordingInteractor.ANY))
        assertEquals(0, interactor.count("updateTrainingName", RecordingInteractor.ANY, RecordingInteractor.ANY))
    }

    @Test
    fun `OnFinishConfirm with valid name calls finishSession atomically`() = runTest {
        interactor.onCall("finishSession", "session-1", "Push Day") { finishResult() }
        stringResolver.answer(Res.string.feature_live_workout_finish_success, value = "Saved")
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                trainingName = "",
                trainingNameLabel = "Untitled",
                dialogState = finishDialog(requiresName = true, nameDraft = "Push Day"),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            stringResolver = stringResolver,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnFinishConfirm)
        store.runLatestLaunch(this)

        // The rename is folded into finishSession's transaction, so it must not fire alone.
        assertEquals(0, interactor.count("updateTrainingName", RecordingInteractor.ANY, RecordingInteractor.ANY))
        assertEquals(1, interactor.count("finishSession", "session-1", "Push Day"))
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
        interactor.onCall("finishSession", "session-1", null) { finishResult() }
        stringResolver.answer(Res.string.feature_live_workout_finish_success, value = "Saved")
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                trainingName = "Push Day",
                trainingNameLabel = "Push Day",
                dialogState = finishDialog(requiresName = false, nameDraft = "Push Day"),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            stringResolver = stringResolver,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnFinishConfirm)
        store.runLatestLaunch(this)

        assertEquals(1, interactor.count("finishSession", "session-1", null))
    }

    @Test
    fun `OnFinishConfirm with required name and missing trainingUuid surfaces FinishFailed`() = runTest {
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                trainingUuid = null,
                trainingName = "",
                dialogState = finishDialog(requiresName = true, nameDraft = "Push Day"),
                liveCopy = LiveWorkoutCopy.EMPTY.copy(errorFinishFailed = "Finish failed"),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            stringResolver = stringResolver,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = store,
        )

        handler.invoke(Action.DialogClick.OnFinishConfirm)

        assertEquals(0, interactor.count("finishSession", RecordingInteractor.ANY, RecordingInteractor.ANY))
        assertTrue(
            store.events.any { it is Event.ShowError && it.message == "Finish failed" },
        )
    }

    @Test
    fun `OnFinishConfirm null result surfaces FinishMissingSession`() = runTest {
        interactor.onCall("finishSession", RecordingInteractor.ANY, RecordingInteractor.ANY) { null }
        stringResolver.answer(Res.string.feature_live_workout_error_finish_missing_session, value = "Missing session")
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                trainingName = "Push Day",
                dialogState = finishDialog(requiresName = false, nameDraft = "Push Day"),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            stringResolver = stringResolver,
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
        interactor.onCall("finishSession", RecordingInteractor.ANY, RecordingInteractor.ANY) {
            throw IllegalStateException("boom")
        }
        stringResolver.answer(Res.string.feature_live_workout_error_finish_failed, value = "Finish failed")
        val store = FakeLiveWorkoutHandlerStore(
            baseState(loggedExercise()).copy(
                trainingName = "Push Day",
                dialogState = finishDialog(requiresName = false, nameDraft = "Push Day"),
            ),
        )
        val handler = DialogClickHandler(
            interactor = interactor,
            stringResolver = stringResolver,
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
        val stateFlow = MutableStateFlow(
            baseState(loggedExercise()).copy(
                dialogState = finishDialog(
                    requiresName = true,
                    nameDraft = "Push Day",
                    nameError = null,
                    confirmEnabled = true,
                ),
                liveCopy = LiveWorkoutCopy.EMPTY.copy(finishNameRequired = "Name is required"),
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

    // region OnDeleteExerciseConfirm

    @Test
    fun `OnDeleteExerciseConfirm fills its toast with the truncated name`() {
        val stateFlow = MutableStateFlow(
            baseState(loggedExercise().copy(exerciseName = "Romanian Deadlift With Pause"))
                .copy(liveCopy = NAMED_COPY),
        )

        handler(stateFlow).invoke(Action.DialogClick.OnDeleteExerciseConfirm("pe-1"))

        assertEquals(
            "toastExerciseRemovedTemplate Romanian Deadlift With P…",
            stateFlow.value.pendingUndo?.message,
        )
    }

    // endregion

    // region PickerAction delegation

    @Test
    fun `PickerAction delegates to the picker sub-handler`() {
        val stateFlow = MutableStateFlow(baseState(loggedExercise()))
        val picker = ExercisePickerAction.OnDismiss

        handler(stateFlow).invoke(Action.DialogClick.PickerAction(picker))

        assertEquals<List<ExercisePickerAction>>(listOf(picker), pickerHandler.actions)
    }

    // endregion

    // region helpers

    private fun handler(stateFlow: MutableStateFlow<State>): DialogClickHandler =
        DialogClickHandler(
            interactor = interactor,
            stringResolver = stringResolver,
            pickerHandler = pickerHandler,
            setMutator = setMutator,
            store = handlerStore(stateFlow),
        )

    private fun handlerStore(stateFlow: MutableStateFlow<State>): LiveWorkoutHandlerStore =
        RelaxedHandlerStore(stateFlow)

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
        private var latestLaunch: (suspend (CoroutineScope) -> Unit)? = null

        val consumed: MutableList<Action> = mutableListOf()
        val consumedOnMain: MutableList<Action> = mutableListOf()
        val events: MutableList<Event> = mutableListOf()

        override val state: StateFlow<State> = stateFlow
        override val lastAction: Action? = null
        override val logger: Logger = SilentLogger()

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

        override fun <T> launch(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            action: suspend CoroutineScope.() -> T,
        ): Job = capture(onError, onSuccess, action)

        override fun <T> launchDefault(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            action: suspend CoroutineScope.() -> T,
        ): Job = capture(onError, onSuccess, action)

        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Job = Job()

        private fun <T> capture(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            action: suspend CoroutineScope.() -> T,
        ): Job {
            // Mirror production: a throw routes through onError, a result through onSuccess.
            latestLaunch = { scope ->
                try {
                    val result = scope.action()
                    scope.onSuccess(result)
                } catch (throwable: Throwable) {
                    onError(throwable)
                }
            }
            return Job()
        }

        suspend fun runLatestLaunch(scope: CoroutineScope) {
            latestLaunch?.invoke(scope)
        }
    }

    // endregion

    /**
     * CM6: the relaxed mock store — [state] is the identity's flow and [updateState] applies to it,
     * a launch is counted and runs nothing, and every other member does nothing.
     */
    private class RelaxedHandlerStore(
        override val state: MutableStateFlow<State>,
    ) : LiveWorkoutHandlerStore {

        var launches: Int = 0
            private set

        override val lastAction: Action? = null

        override val logger: Logger = SilentLogger()

        override fun sendEvent(event: Event) = Unit

        override fun consume(action: Action) = Unit

        override suspend fun consumeOnMain(action: Action) = Unit

        override fun updateState(update: (State) -> State) {
            state.value = update(state.value)
        }

        override suspend fun updateStateImmediate(update: suspend (State) -> State) = Unit

        override suspend fun updateStateImmediate(state: State) = Unit

        override fun <T> launch(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            action: suspend CoroutineScope.() -> T,
        ): Job {
            launches += 1
            return Job()
        }

        override fun <T> launchDefault(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            action: suspend CoroutineScope.() -> T,
        ): Job = Job()

        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Job = Job()
    }

    /**
     * CM6: the relaxed interactor as a log. Every call is kept in order as a [Call], so a verify is a
     * count over [calls]; [onCall] answers what an identity sets up, a throw included, and any other
     * call gets the relaxed mock's answer. A member whose relaxed answer no identity relies on fails
     * fast when it is left unanswered (CM11).
     */
    private class RecordingInteractor : LiveWorkoutInteractor {

        data class Call(val member: String, val args: List<Any?>)

        val calls = mutableListOf<Call>()
        private val answers = mutableListOf<Pair<Call, () -> Any?>>()

        /** Answers [member] called with [args], where [ANY] matches every value; the latest set-up wins. */
        fun onCall(member: String, vararg args: Any?, answer: () -> Any?) {
            answers += Call(member, args.toList()) to answer
        }

        /** The calls of [member] whose arguments match [args], where [ANY] matches every value. */
        fun count(member: String, vararg args: Any?): Int = calls.count { it.matches(Call(member, args.toList())) }

        private fun Call.matches(expected: Call): Boolean =
            member == expected.member &&
                args.size == expected.args.size &&
                args.zip(expected.args).all { (actual, wanted) -> wanted === ANY || wanted == actual }

        private fun answer(member: String, vararg args: Any?, relaxed: () -> Any?): Any? {
            val call = Call(member, args.toList())
            calls += call
            val answer = answers.lastOrNull { (expected, _) -> call.matches(expected) }?.second ?: relaxed
            return answer()
        }

        private fun unanswered(member: String): Nothing = error("no identity sets up $member")

        override suspend fun startSession(trainingUuid: String): String =
            answer("startSession", trainingUuid) { "" } as String

        override suspend fun createAdhocSession(name: String, exerciseUuids: List<String>): AdhocSessionResult =
            answer("createAdhocSession", name, exerciseUuids) { unanswered("createAdhocSession") }
                as AdhocSessionResult

        override suspend fun addExerciseToActiveSession(
            sessionUuid: String,
            trainingUuid: String,
            exerciseUuid: String,
            attachToPlan: Boolean,
        ): AddExerciseResult = answer(
            "addExerciseToActiveSession",
            sessionUuid,
            trainingUuid,
            exerciseUuid,
            attachToPlan,
        ) { unanswered("addExerciseToActiveSession") } as AddExerciseResult

        override suspend fun discardAdhocSession(sessionUuid: String, trainingUuid: String) {
            answer("discardAdhocSession", sessionUuid, trainingUuid) {}
        }

        override suspend fun createInlineAdhocExercise(name: String): InlineAdhocResult =
            answer("createInlineAdhocExercise", name) { unanswered("createInlineAdhocExercise") }
                as InlineAdhocResult

        override suspend fun updateTrainingName(trainingUuid: String, name: String) {
            answer("updateTrainingName", trainingUuid, name) {}
        }

        override suspend fun searchExercisesForPicker(
            query: String,
            excludedUuids: Set<String>,
        ): List<ExercisePickerEntry> =
            (answer("searchExercisesForPicker", query, excludedUuids) { emptyList<ExercisePickerEntry>() } as List<*>)
                .map { entry -> entry as ExercisePickerEntry }

        override suspend fun fetchPrSnapshotForExercise(exerciseUuid: String): PersonalRecordDomain? =
            answer("fetchPrSnapshotForExercise", exerciseUuid) { null } as PersonalRecordDomain?

        override suspend fun loadSession(sessionUuid: String): SessionSnapshotDomain? =
            answer("loadSession", sessionUuid) { unanswered("loadSession") } as SessionSnapshotDomain?

        override fun observeExternalWrites(sessionUuid: String): Nothing = unanswered("observeExternalWrites")

        override suspend fun upsertSet(performedExerciseUuid: String, position: Int, set: PlanSetDomain) {
            answer("upsertSet", performedExerciseUuid, position, set) {}
        }

        override suspend fun deleteSet(performedExerciseUuid: String, position: Int) {
            answer("deleteSet", performedExerciseUuid, position) {}
        }

        override suspend fun setSkipped(performedExerciseUuid: String, skipped: Boolean) {
            answer("setSkipped", performedExerciseUuid, skipped) {}
        }

        override suspend fun deleteExerciseFromSession(
            performedExerciseUuid: String,
            exerciseUuid: String,
            trainingUuid: String?,
            removeFromPlan: Boolean,
        ) {
            answer("deleteExerciseFromSession", performedExerciseUuid, exerciseUuid, trainingUuid, removeFromPlan) {}
        }

        override suspend fun setPlanAttachment(
            trainingUuid: String,
            exerciseUuid: String,
            attached: Boolean,
            planSets: List<PlanSetDomain>?,
        ) {
            answer("setPlanAttachment", trainingUuid, exerciseUuid, attached, planSets) {}
        }

        override suspend fun resetExerciseSets(performedExerciseUuid: String) {
            answer("resetExerciseSets", performedExerciseUuid) {}
        }

        override suspend fun finishSession(sessionUuid: String, newTrainingName: String?): FinishResult? =
            answer("finishSession", sessionUuid, newTrainingName) { unanswered("finishSession") } as FinishResult?

        override suspend fun cancelSession(sessionUuid: String) {
            answer("cancelSession", sessionUuid) {}
        }

        override suspend fun setPlanForExercise(
            trainingUuid: String,
            exerciseUuid: String,
            plan: List<PlanSetDomain>?,
        ) {
            answer("setPlanForExercise", trainingUuid, exerciseUuid, plan) {}
        }

        override suspend fun setAdhocPlan(exerciseUuid: String, plan: List<PlanSetDomain>?) {
            answer("setAdhocPlan", exerciseUuid, plan) {}
        }

        companion object {

            /** Matches every argument value, as MockK's `any()` did. */
            val ANY = Any()
        }
    }

    /** CM6: answers what an identity sets up, by resource and arguments, and `""` otherwise. */
    private class RelaxedStringResolver : StringResolver {

        private val answers = mutableMapOf<Pair<StringResource, List<Any>>, String>()

        fun answer(resource: StringResource, vararg formatArgs: Any, value: String) {
            answers[resource to formatArgs.toList()] = value
        }

        override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String =
            answers[resource to formatArgs.toList()].orEmpty()

        override suspend fun getPluralString(
            resource: PluralStringResource,
            quantity: Int,
            vararg formatArgs: Any,
        ): Nothing = error("no identity here reads a plural; got $resource")
    }

    /** The relaxed logger: every record is dropped. */
    private class SilentLogger : Logger {

        override fun e(throwable: Throwable, message: String?) = Unit

        override fun d(message: String) = Unit

        override fun d(e: Throwable, message: String) = Unit

        override fun d(e: Throwable, message: () -> String) = Unit

        override fun d(message: () -> String) = Unit

        override fun i(message: String) = Unit

        override fun i(message: () -> String) = Unit

        override fun v(message: String) = Unit

        override fun v(message: () -> String) = Unit

        override fun w(message: String) = Unit

        override fun w(message: () -> String) = Unit

        override fun w(message: String, throwable: Throwable) = Unit

        override fun w(throwable: Throwable, message: () -> String) = Unit
    }
}
