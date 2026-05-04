// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.mvi.handler

import io.github.stslex.workeeper.core.ui.plan_editor.di.PlanEditorHandlerStore
import io.github.stslex.workeeper.core.ui.plan_editor.domain.PlanEditorInteractor
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.State
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ClickHandlerTest {

    private val interactor = mockk<PlanEditorInteractor>(relaxed = true)
    private val initialState = State.init(State.Mode.Exercise(exerciseUuid = "exercise-1"))
    private val stateFlow = MutableStateFlow(initialState)

    private val store = mockk<PlanEditorHandlerStore>(relaxed = true).apply {
        every { state } returns stateFlow
        every { updateState(any()) } answers {
            val update = firstArg<(State) -> State>()
            stateFlow.value = update(stateFlow.value)
        }
    }

    private val handler = ClickHandler(interactor = interactor, store = store)

    @Test
    fun `OnAddSet appends a new work set with default reps when draft is empty`() {
        handler.invoke(Action.Click.OnAddSet)

        assertEquals(1, stateFlow.value.draft.size)
        val added = stateFlow.value.draft.first()
        assertEquals(SetTypeUiModel.WORK, added.type)
    }

    @Test
    fun `OnAddSet copies reps from previous set when draft has rows`() {
        stateFlow.value = stateFlow.value.copy(
            draft = listOf(PlanSetUiModel(80.0, 8, SetTypeUiModel.WARMUP)).toImmutableList(),
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
        stateFlow.value = stateFlow.value.copy(
            draft = listOf(
                PlanSetUiModel(60.0, 10, SetTypeUiModel.WARMUP),
                PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK),
                PlanSetUiModel(100.0, 5, SetTypeUiModel.WORK),
            ).toImmutableList(),
        )

        handler.invoke(Action.Click.OnSetRemove(index = 1))

        assertEquals(2, stateFlow.value.draft.size)
        assertEquals(60.0, stateFlow.value.draft[0].weight)
        assertEquals(100.0, stateFlow.value.draft[1].weight)
    }

    @Test
    fun `OnSetRemove with out-of-bounds index leaves draft unchanged`() {
        val draft = listOf(PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK)).toImmutableList()
        stateFlow.value = stateFlow.value.copy(draft = draft)

        handler.invoke(Action.Click.OnSetRemove(index = 5))

        assertEquals(draft, stateFlow.value.draft)
    }

    @Test
    fun `OnSetTypeChange updates the type of the row at the given index`() {
        stateFlow.value = stateFlow.value.copy(
            draft = listOf(
                PlanSetUiModel(60.0, 10, SetTypeUiModel.WORK),
                PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK),
            ).toImmutableList(),
        )

        handler.invoke(
            Action.Click.OnSetTypeChange(index = 1, value = SetTypeUiModel.FAILURE),
        )

        assertEquals(SetTypeUiModel.FAILURE, stateFlow.value.draft[1].type)
        assertEquals(SetTypeUiModel.WORK, stateFlow.value.draft[0].type)
    }

    @Test
    fun `OnBackClick on clean state dispatches Navigation Back`() {
        handler.invoke(Action.Click.OnBackClick)

        verify(exactly = 1) { store.consume(Action.Navigation.Back) }
        assertFalse(stateFlow.value.confirmDiscardOpen)
    }

    @Test
    fun `OnBackClick on dirty state opens discard dialog instead of popping`() {
        // Make the state dirty by appending a fresh set (initial draft was empty).
        val dirtyDraft = listOf(
            PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK),
        ).toImmutableList()
        stateFlow.value = stateFlow.value.copy(draft = dirtyDraft)

        handler.invoke(Action.Click.OnBackClick)

        assertTrue(stateFlow.value.confirmDiscardOpen)
        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
    }

    @Test
    fun `OnDismissDiscard closes the dialog without navigating`() {
        stateFlow.value = stateFlow.value.copy(confirmDiscardOpen = true)

        handler.invoke(Action.Click.OnDismissDiscard)

        assertFalse(stateFlow.value.confirmDiscardOpen)
        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
    }

    @Test
    fun `OnConfirmDiscard closes dialog and navigates back without persisting`() {
        stateFlow.value = stateFlow.value.copy(confirmDiscardOpen = true)

        handler.invoke(Action.Click.OnConfirmDiscard)

        assertFalse(stateFlow.value.confirmDiscardOpen)
        verify(exactly = 1) { store.consume(Action.Navigation.Back) }
    }

    @Test
    fun `state is dirty when draft differs from initialDraft`() {
        val initial = listOf(PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK)).toImmutableList()
        stateFlow.value = stateFlow.value.copy(initialDraft = initial, draft = initial)
        assertFalse(stateFlow.value.isDirty)

        stateFlow.value = stateFlow.value.copy(
            draft = (initial + PlanSetUiModel(100.0, 5, SetTypeUiModel.WORK))
                .toImmutableList(),
        )
        assertTrue(stateFlow.value.isDirty)
    }

    @Test
    fun `interceptBack disabled while discard dialog is shown`() {
        stateFlow.value = stateFlow.value.copy(
            initialDraft = persistentListOf(),
            draft = listOf(PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK)).toImmutableList(),
            confirmDiscardOpen = true,
        )

        // Dirty + dialog open → interceptBack is false so Dialog's own dismiss handles
        // the back gesture (predictive-back preview behind the dialog isn't desirable
        // anyway, but we explicitly de-arm BackHandler so the system gesture is a no-op
        // beyond closing the dialog).
        assertFalse(stateFlow.value.interceptBack)
    }
}
