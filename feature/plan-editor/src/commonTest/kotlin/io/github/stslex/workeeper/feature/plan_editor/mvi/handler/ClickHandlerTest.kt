// SPDX-License-Identifier: GPL-3.0-only
@file:Suppress("INVALID_CHARACTERS_NATIVE_ERROR")

package io.github.stslex.workeeper.feature.plan_editor.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorHandlerStore
import io.github.stslex.workeeper.feature.plan_editor.domain.PlanEditorInteractor
import io.github.stslex.workeeper.feature.plan_editor.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.plan_editor.domain.model.PlanEditorLoadResult
import io.github.stslex.workeeper.feature.plan_editor.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ClickHandlerTest {

    private fun setup(initialState: State): TestSetup {
        val store = FakePlanEditorHandlerStore(initialState)
        val interactor = RecordingPlanEditorInteractor()
        return TestSetup(
            stateFlow = store.mutableState,
            store = store,
            handler = ClickHandler(
                interactor = interactor,
                store = store,
            ),
            interactor = interactor,
        )
    }

    private fun existingExerciseInitial(): State = State.init(
        mode = State.Mode.Exercise(exerciseUuid = "exercise-1"),
        seedType = ExerciseTypeUiModel.WEIGHTED,
        seedPlan = persistentListOf(),
    )

    private data class TestSetup(
        val stateFlow: MutableStateFlow<State>,
        val store: FakePlanEditorHandlerStore,
        val handler: ClickHandler,
        val interactor: RecordingPlanEditorInteractor,
    )

    @Test
    fun `OnAddSet appends a new work set with default reps when draft is empty`() {
        val (stateFlow, _, handler) = setup(existingExerciseInitial())
        handler.invoke(Action.Click.OnAddSet)

        assertEquals(1, stateFlow.value.draft.size)
        val added = stateFlow.value.draft.first()
        assertEquals(SetTypeUiModel.WORK, added.type)
    }

    @Test
    fun `OnAddSet copies reps from previous set when draft has rows`() {
        val (stateFlow, _, handler) = setup(
            existingExerciseInitial().copy(
                draft = listOf(PlanSetUiModel(80.0, 8, SetTypeUiModel.WARMUP))
                    .toImmutableList(),
            ),
        )

        handler.invoke(Action.Click.OnAddSet)

        assertEquals(2, stateFlow.value.draft.size)
        val added = stateFlow.value.draft.last()
        assertEquals(8, added.reps)
        // A new set always cycles back to WORK: warmups precede work sets.
        assertEquals(SetTypeUiModel.WORK, added.type)
    }

    @Test
    fun `OnSetRemove drops the row at the given index`() {
        val (stateFlow, _, handler) = setup(
            existingExerciseInitial().copy(
                draft = listOf(
                    PlanSetUiModel(60.0, 10, SetTypeUiModel.WARMUP),
                    PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK),
                    PlanSetUiModel(100.0, 5, SetTypeUiModel.WORK),
                ).toImmutableList(),
            ),
        )

        handler.invoke(Action.Click.OnSetRemove(index = 1))

        assertEquals(2, stateFlow.value.draft.size)
        assertEquals(60.0, stateFlow.value.draft[0].weight)
        assertEquals(100.0, stateFlow.value.draft[1].weight)
    }

    @Test
    fun `OnSetRemove with out-of-bounds index leaves draft unchanged`() {
        val draft = listOf(PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK)).toImmutableList()
        val (stateFlow, _, handler) = setup(existingExerciseInitial().copy(draft = draft))

        handler.invoke(Action.Click.OnSetRemove(index = 5))

        assertEquals(draft, stateFlow.value.draft)
    }

    @Test
    fun `OnSetTypeChange updates the type of the row at the given index`() {
        val (stateFlow, _, handler) = setup(
            existingExerciseInitial().copy(
                draft = listOf(
                    PlanSetUiModel(60.0, 10, SetTypeUiModel.WORK),
                    PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK),
                ).toImmutableList(),
            ),
        )

        handler.invoke(
            Action.Click.OnSetTypeChange(index = 1, value = SetTypeUiModel.FAILURE),
        )

        assertEquals(SetTypeUiModel.FAILURE, stateFlow.value.draft[1].type)
        assertEquals(SetTypeUiModel.WORK, stateFlow.value.draft[0].type)
    }

    @Test
    fun `OnTypeToggle to same type is no-op`() {
        val (stateFlow, store, handler) = setup(existingExerciseInitial())
        handler.invoke(Action.Click.OnTypeToggle(ExerciseTypeUiModel.WEIGHTED))

        assertEquals(ExerciseTypeUiModel.WEIGHTED, stateFlow.value.type)
        assertEquals(emptyList(), store.events)
    }

    @Test
    fun `OnTypeToggle with empty draft applies new type silently without dialog`() {
        val (stateFlow, _, handler) = setup(existingExerciseInitial())

        handler.invoke(Action.Click.OnTypeToggle(ExerciseTypeUiModel.WEIGHTLESS))

        assertEquals(ExerciseTypeUiModel.WEIGHTLESS, stateFlow.value.type)
        assertTrue(stateFlow.value.dialogState is DialogState.Hidden)
        assertNull(stateFlow.value.pendingTypeChange)
    }

    @Test
    fun `OnTypeToggle WEIGHTED to WEIGHTLESS with weighted draft opens confirm dialog`() {
        val (stateFlow, _, handler) = setup(
            existingExerciseInitial().copy(
                draft = listOf(
                    PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
                ).toImmutableList(),
            ),
        )

        handler.invoke(Action.Click.OnTypeToggle(ExerciseTypeUiModel.WEIGHTLESS))

        assertEquals(DialogState.TypeChangeConfirm, stateFlow.value.dialogState)
        // Type stays WEIGHTED until the user confirms; the target waits in `pendingTypeChange`.
        assertEquals(ExerciseTypeUiModel.WEIGHTED, stateFlow.value.type)
        assertEquals(ExerciseTypeUiModel.WEIGHTLESS, stateFlow.value.pendingTypeChange)
    }

    @Test
    fun `OnTypeToggle WEIGHTLESS to WEIGHTED applies new type silently regardless of draft`() {
        val (stateFlow, _, handler) = setup(
            existingExerciseInitial().copy(
                type = ExerciseTypeUiModel.WEIGHTLESS,
                initialType = ExerciseTypeUiModel.WEIGHTLESS,
                draft = listOf(
                    PlanSetUiModel(weight = null, reps = 8, type = SetTypeUiModel.WORK),
                ).toImmutableList(),
            ),
        )

        handler.invoke(Action.Click.OnTypeToggle(ExerciseTypeUiModel.WEIGHTED))

        // Going weightless → weighted never strands data — no confirm needed.
        assertEquals(ExerciseTypeUiModel.WEIGHTED, stateFlow.value.type)
        assertTrue(stateFlow.value.dialogState is DialogState.Hidden)
    }

    @Test
    fun `OnTypeChangeConfirm wipes weights from draft, applies type, hides dialog`() {
        val (stateFlow, _, handler) = setup(
            existingExerciseInitial().copy(
                pendingTypeChange = ExerciseTypeUiModel.WEIGHTLESS,
                dialogState = DialogState.TypeChangeConfirm,
                draft = listOf(
                    PlanSetUiModel(50.0, 8, SetTypeUiModel.WORK),
                    PlanSetUiModel(60.0, 6, SetTypeUiModel.FAILURE),
                ).toImmutableList(),
            ),
        )

        handler.invoke(Action.Click.OnTypeChangeConfirm)

        assertEquals(ExerciseTypeUiModel.WEIGHTLESS, stateFlow.value.type)
        assertNull(stateFlow.value.pendingTypeChange)
        assertTrue(stateFlow.value.dialogState is DialogState.Hidden)
        assertTrue(stateFlow.value.draft.all { it.weight == null })
    }

    @Test
    fun `OnTypeChangeDismiss clears pending and hides dialog without changing type`() {
        val (stateFlow, _, handler) = setup(
            existingExerciseInitial().copy(
                pendingTypeChange = ExerciseTypeUiModel.WEIGHTLESS,
                dialogState = DialogState.TypeChangeConfirm,
            ),
        )

        handler.invoke(Action.Click.OnTypeChangeDismiss)

        assertEquals(ExerciseTypeUiModel.WEIGHTED, stateFlow.value.type)
        assertNull(stateFlow.value.pendingTypeChange)
        assertTrue(stateFlow.value.dialogState is DialogState.Hidden)
    }

    @Test
    fun `OnBackClick with open dialog dismisses dialog before propagating`() {
        val (stateFlow, store, handler) = setup(
            existingExerciseInitial().copy(
                pendingTypeChange = ExerciseTypeUiModel.WEIGHTLESS,
                dialogState = DialogState.TypeChangeConfirm,
            ),
        )

        handler.invoke(Action.Click.OnBackClick)

        assertTrue(stateFlow.value.dialogState is DialogState.Hidden)
        assertNull(stateFlow.value.pendingTypeChange)
        assertEquals(emptyList(), store.consumedActions)
    }

    @Test
    fun `OnBackClick on clean state dispatches Navigation Back`() {
        val (_, store, handler) = setup(existingExerciseInitial())
        handler.invoke(Action.Click.OnBackClick)

        assertEquals(listOf<Action>(Action.Navigation.Back), store.consumedActions)
    }

    @Test
    fun `OnBackClick on dirty state opens discard dialog instead of popping`() {
        // Make the state dirty by appending a fresh set (initial draft was empty).
        val dirtyDraft = listOf(
            PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK),
        ).toImmutableList()
        val (stateFlow, store, handler) = setup(
            existingExerciseInitial().copy(draft = dirtyDraft),
        )

        handler.invoke(Action.Click.OnBackClick)

        assertEquals(DialogState.DiscardConfirm, stateFlow.value.dialogState)
        assertEquals(emptyList(), store.consumedActions)
    }

    @Test
    fun `OnDismissDiscard closes the sheet without navigating`() {
        val (stateFlow, store, handler) = setup(
            existingExerciseInitial().copy(dialogState = DialogState.DiscardConfirm),
        )

        handler.invoke(Action.Click.OnDismissDiscard)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        assertEquals(emptyList(), store.consumedActions)
    }

    @Test
    fun `OnConfirmDiscard closes the sheet and navigates back without persisting`() {
        val (stateFlow, store, handler, interactor) = setup(
            existingExerciseInitial().copy(dialogState = DialogState.DiscardConfirm),
        )

        handler.invoke(Action.Click.OnConfirmDiscard)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        assertEquals(listOf<Action>(Action.Navigation.Back), store.consumedActions)
        assertEquals(emptyList(), interactor.saveRequests)
    }

    @Test
    fun `the discard sheet and the type-change sheet cannot be open at once`() {
        val (stateFlow, _, handler) = setup(
            existingExerciseInitial().copy(
                draft = listOf(PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK)).toImmutableList(),
                dialogState = DialogState.TypeChangeConfirm,
            ),
        )

        // Back with a modal already open closes it; it does NOT stack the discard sheet on top.
        handler.invoke(Action.Click.OnBackClick)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    @Test
    fun `back with the discard sheet open hides it and never navigates`() {
        val (stateFlow, store, handler) = setup(
            existingExerciseInitial().copy(
                draft = listOf(PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK)).toImmutableList(),
                dialogState = DialogState.DiscardConfirm,
            ),
        )

        handler.invoke(Action.Click.OnBackClick)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        assertEquals(emptyList(), store.consumedActions)
    }

    @Test
    fun `state is dirty when draft differs from initialDraft`() {
        val initial = listOf(PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK)).toImmutableList()
        val (stateFlow, _, _) = setup(
            existingExerciseInitial().copy(initialDraft = initial, draft = initial),
        )
        assertFalse(stateFlow.value.isDirty)

        stateFlow.value = stateFlow.value.copy(
            draft = (initial + PlanSetUiModel(100.0, 5, SetTypeUiModel.WORK))
                .toImmutableList(),
        )
        assertTrue(stateFlow.value.isDirty)
    }

    @Test
    fun `state is dirty when type differs from initialType even with stable draft`() {
        val (stateFlow, _, _) = setup(existingExerciseInitial())
        assertFalse(stateFlow.value.isDirty)

        stateFlow.value = stateFlow.value.copy(type = ExerciseTypeUiModel.WEIGHTLESS)
        assertTrue(stateFlow.value.isDirty)
    }

    @Test
    fun `interceptBack stays armed while the discard sheet is shown`() {
        val (stateFlow, _, _) = setup(
            existingExerciseInitial().copy(
                draft = listOf(PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK)).toImmutableList(),
                dialogState = DialogState.DiscardConfirm,
            ),
        )

        assertTrue(stateFlow.value.interceptBack)
    }

    @Test
    fun `interceptBack stays armed while the type-change sheet is shown`() {
        val (stateFlow, _, _) = setup(
            existingExerciseInitial().copy(
                dialogState = DialogState.TypeChangeConfirm,
            ),
        )

        assertTrue(stateFlow.value.interceptBack)
    }

    @Test
    fun `interceptBack stays enabled when type-change confirm dialog is open`() {
        val (stateFlow, _, _) = setup(
            existingExerciseInitial().copy(
                pendingTypeChange = ExerciseTypeUiModel.WEIGHTLESS,
                dialogState = DialogState.TypeChangeConfirm,
            ),
        )

        assertTrue(stateFlow.value.interceptBack)
    }
}

private class FakePlanEditorHandlerStore(
    initialState: State,
) : PlanEditorHandlerStore {

    val mutableState = MutableStateFlow(initialState)
    val events = mutableListOf<Event>()
    val consumedActions = mutableListOf<Action>()

    override val state: StateFlow<State> = mutableState

    override val lastAction: Action?
        get() = error("lastAction must not be read by ClickHandler")

    override val logger: Logger
        get() = error("logger must not be read by ClickHandler")

    override fun sendEvent(event: Event) {
        events += event
    }

    override fun consume(action: Action) {
        consumedActions += action
    }

    override fun updateState(update: (State) -> State) {
        mutableState.value = update(mutableState.value)
    }

    override suspend fun consumeOnMain(action: Action): Nothing =
        error("consumeOnMain must not be used by these ClickHandler tests")

    override suspend fun updateStateImmediate(update: suspend (State) -> State): Nothing =
        error("updateStateImmediate(update) must not be used by ClickHandler")

    override suspend fun updateStateImmediate(state: State): Nothing =
        error("updateStateImmediate(state) must not be used by ClickHandler")

    override fun <T> launch(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        action: suspend CoroutineScope.() -> T,
    ): Job = error("launch must not be used by these ClickHandler tests")

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

private data class SaveRequest(
    val exerciseUuid: String,
    val trainingUuid: String?,
    val type: ExerciseTypeDomain,
    val plan: List<PlanSetDomain>?,
)

private class RecordingPlanEditorInteractor : PlanEditorInteractor {

    val saveRequests = mutableListOf<SaveRequest>()

    override suspend fun loadPlan(
        exerciseUuid: String,
        trainingUuid: String?,
    ): PlanEditorLoadResult = error("loadPlan must not be used by ClickHandler")

    override suspend fun savePlan(
        exerciseUuid: String,
        trainingUuid: String?,
        type: ExerciseTypeDomain,
        plan: List<PlanSetDomain>?,
    ) {
        saveRequests += SaveRequest(exerciseUuid, trainingUuid, type, plan)
    }
}
