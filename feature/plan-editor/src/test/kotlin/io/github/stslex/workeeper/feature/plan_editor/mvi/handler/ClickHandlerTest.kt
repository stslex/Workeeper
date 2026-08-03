// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.mvi.handler

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanDraftResult
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorHandlerStore
import io.github.stslex.workeeper.feature.plan_editor.domain.PlanEditorInteractor
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ClickHandlerTest {

    private val interactor = mockk<PlanEditorInteractor>(relaxed = true)
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true).apply {
        every { getString(any()) } returns "label"
        every { getString(any(), *anyVararg()) } returns "label"
    }

    private fun setup(initialState: State): TestSetup {
        val stateFlow = MutableStateFlow(initialState)
        val store = mockk<PlanEditorHandlerStore>(relaxed = true).apply {
            every { state } returns stateFlow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
        }
        return TestSetup(
            stateFlow = stateFlow,
            store = store,
            handler = ClickHandler(
                interactor = interactor,
                resourceWrapper = resourceWrapper,
                store = store,
            ),
        )
    }

    private fun existingExerciseInitial(): State = State.init(
        mode = State.Mode.Exercise(exerciseUuid = "exercise-1"),
        seedType = ExerciseTypeUiModel.WEIGHTED,
        seedPlan = persistentListOf(),
    )

    private fun draftInitial(): State = State.init(
        mode = State.Mode.Draft,
        seedType = ExerciseTypeUiModel.WEIGHTED,
        seedPlan = persistentListOf(),
    )

    private data class TestSetup(
        val stateFlow: MutableStateFlow<State>,
        val store: PlanEditorHandlerStore,
        val handler: ClickHandler,
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
        // New set always cycles back to WORK regardless of previous type — workout
        // pattern: warmups precede work sets, so the next add is a work set by default.
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
        verify(exactly = 0) { store.sendEvent(any()) }
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

        assertTrue(stateFlow.value.dialogState is DialogState.TypeChangeConfirm)
        // Type stays WEIGHTED until the user confirms — pending lives in
        // `pendingTypeChange` and is committed by `OnTypeChangeConfirm`.
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
                dialogState = DialogState.TypeChangeConfirm(
                    title = "t",
                    body = "b",
                    impactSummary = "i",
                    confirmLabel = "c",
                ),
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
                dialogState = DialogState.TypeChangeConfirm("t", "b", "i", "c"),
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
                dialogState = DialogState.TypeChangeConfirm("t", "b", "i", "c"),
            ),
        )

        handler.invoke(Action.Click.OnBackClick)

        assertTrue(stateFlow.value.dialogState is DialogState.Hidden)
        assertNull(stateFlow.value.pendingTypeChange)
        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
    }

    @Test
    fun `OnBackClick on clean state dispatches Navigation Back`() {
        val (_, store, handler) = setup(existingExerciseInitial())
        handler.invoke(Action.Click.OnBackClick)

        verify(exactly = 1) { store.consume(Action.Navigation.Back) }
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
        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
    }

    @Test
    fun `OnDismissDiscard closes the sheet without navigating`() {
        val (stateFlow, store, handler) = setup(
            existingExerciseInitial().copy(dialogState = DialogState.DiscardConfirm),
        )

        handler.invoke(Action.Click.OnDismissDiscard)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
    }

    @Test
    fun `OnConfirmDiscard closes the sheet and navigates back without persisting`() {
        val (stateFlow, store, handler) = setup(
            existingExerciseInitial().copy(dialogState = DialogState.DiscardConfirm),
        )

        handler.invoke(Action.Click.OnConfirmDiscard)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        verify(exactly = 1) { store.consume(Action.Navigation.Back) }
    }

    /**
     * The one-channel invariant, and the one a `Boolean` beside a sealed field cannot state:
     * **the two modals are mutually exclusive by construction** (§26; `mvi-dialog-state`). With a
     * second field, "discard open" and "type-change open" is a reachable pair and the screen draws
     * both.
     */
    @Test
    fun `the discard sheet and the type-change sheet cannot be open at once`() {
        val (stateFlow, _, handler) = setup(
            existingExerciseInitial().copy(
                draft = listOf(PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK)).toImmutableList(),
                dialogState = DialogState.TypeChangeConfirm(
                    title = "t",
                    body = "b",
                    impactSummary = "i",
                    confirmLabel = "c",
                ),
            ),
        )

        // Back with a modal already open closes it; it does NOT stack the discard sheet on top.
        handler.invoke(Action.Click.OnBackClick)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    /**
     * **The discard sheet is not exempt from the one modal rule, and it must never navigate.**
     *
     * This arm is a fallback rather than the live path: every modal here is an `AppConfirmSheet`,
     * which owns back inside its own `ComponentDialog` window and routes it to `onDismissRequest`
     * before the route's handler sees anything. What the assertion protects is the *shape* of the
     * fallback — a variant that navigated away instead would turn a stray back press into a silent
     * discard of the draft.
     */
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
        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
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

    /**
     * **No variant is exempt from interception**, the discard sheet included. An exception here
     * would describe a flow that cannot happen: an `AppConfirmSheet` is a `ModalBottomSheet`, it
     * owns back inside its own `ComponentDialog` window, and the route never sees the press while
     * one is up — so disabling interception for a variant routes nothing anywhere.
     */
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

    /** The other half of the same predicate: any OTHER open modal intercepts too. */
    @Test
    fun `interceptBack stays armed while the type-change sheet is shown`() {
        val (stateFlow, _, _) = setup(
            existingExerciseInitial().copy(
                dialogState = DialogState.TypeChangeConfirm(
                    title = "t",
                    body = "b",
                    impactSummary = "i",
                    confirmLabel = "c",
                ),
            ),
        )

        assertTrue(stateFlow.value.interceptBack)
    }

    @Test
    fun `interceptBack stays enabled when type-change confirm dialog is open`() {
        val (stateFlow, _, _) = setup(
            existingExerciseInitial().copy(
                pendingTypeChange = ExerciseTypeUiModel.WEIGHTLESS,
                dialogState = DialogState.TypeChangeConfirm("t", "b", "i", "c"),
            ),
        )

        // Type-change confirm uses BackHandler interception so the system back gesture
        // routes through `OnBackClick` → dialog dismiss before propagating to a pop.
        assertTrue(stateFlow.value.interceptBack)
    }

    @Test
    fun `OnSave in Draft mode encodes plan and pops with BackAfterDraftSave`() {
        val draft = listOf(
            PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK),
            PlanSetUiModel(90.0, 5, SetTypeUiModel.WORK),
        )
        val (_, store, handler) = setup(
            draftInitial().copy(
                draft = draft.toImmutableList(),
            ),
        )

        handler.invoke(Action.Click.OnSave)

        // Mode.Draft never persists to DB — interactor is untouched.
        coVerify(exactly = 0) {
            interactor.savePlan(any(), any(), any(), any())
        }

        val captured = slot<Action.Navigation.BackAfterDraftSave>()
        verify { store.consume(capture(captured)) }
        val decoded = Json.decodeFromString<PlanDraftResult>(captured.captured.resultJson)
        assertEquals(ExerciseTypeUiModel.WEIGHTED, decoded.type)
        assertEquals(draft, decoded.plan)
    }
}
