// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagItem
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.domain.model.ActiveSessionDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseChangeDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.SaveResult
import io.github.stslex.workeeper.feature.exercise.domain.model.TagDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.TrackNowConflict
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageDisplay
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PendingImage
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.DiscardTarget
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State.Mode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

internal class ClickHandlerTest {

    private val interactor = mockk<ExerciseInteractor>(relaxed = true)
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    private val context = mockk<Context>(relaxed = true).apply {
        every { packageName } returns "io.github.stslex.workeeper.test"
        every { checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_GRANTED
    }

    private fun setup(initialState: State = State.create(uuid = "uuid-1")): TestSetup {
        val stateFlow = MutableStateFlow(initialState)
        val store = mockk<ExerciseHandlerStore>(relaxed = true).apply {
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
                context = context,
                mainDispatcher = Dispatchers.Unconfined,
                store = store,
            ),
        )
    }

    private fun wireSynchronousLaunch(
        store: ExerciseHandlerStore,
        stateFlow: MutableStateFlow<State>,
    ) {
        coEvery { store.updateStateImmediate(any<suspend (State) -> State>()) } coAnswers {
            val update = firstArg<suspend (State) -> State>()
            stateFlow.value = update(stateFlow.value)
        }
        every {
            store.launch(any(), any(), any(), any(), any<suspend CoroutineScope.() -> Any?>())
        } answers {
            val onSuccess = arg<suspend CoroutineScope.(Any?) -> Unit>(1)
            val action = arg<suspend CoroutineScope.() -> Any?>(4)
            runBlocking { onSuccess(this, action()) }
            mockk(relaxed = true)
        }
    }

    /**
     * Creation owns the type on the form now, so these are the assertions that the toggle it
     * gained actually decides something. Weight-bearing rows make the switch destructive, so it
     * asks; nothing to lose makes it immediate.
     */
    @Test
    fun `OnTypeToggle to WEIGHTLESS with weighted rows raises the confirm instead of switching`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = null).copy(
                type = ExerciseTypeUiModel.WEIGHTED,
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
                ),
            ),
        )

        handler.invoke(Action.Click.OnTypeToggle(ExerciseTypeUiModel.WEIGHTLESS))

        // The type has NOT moved yet — the sheet is the opt-in.
        assertEquals(ExerciseTypeUiModel.WEIGHTED, stateFlow.value.type)
        assertEquals(ExerciseTypeUiModel.WEIGHTLESS, stateFlow.value.pendingTypeChange)
        assertTrue(stateFlow.value.dialogState is DialogState.TypeChangeConfirm)
    }

    @Test
    fun `OnTypeToggle to WEIGHTLESS with no weights switches immediately and asks nothing`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = null).copy(
                type = ExerciseTypeUiModel.WEIGHTED,
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = null, reps = 8, type = SetTypeUiModel.WORK),
                ),
            ),
        )

        handler.invoke(Action.Click.OnTypeToggle(ExerciseTypeUiModel.WEIGHTLESS))

        assertEquals(ExerciseTypeUiModel.WEIGHTLESS, stateFlow.value.type)
        assertNull(stateFlow.value.pendingTypeChange)
        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    @Test
    fun `OnTypeChangeConfirm commits the switch and clears the weights it warned about`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = null).copy(
                type = ExerciseTypeUiModel.WEIGHTED,
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
                    PlanSetUiModel(weight = 90.0, reps = 5, type = SetTypeUiModel.WORK),
                ),
            ),
        )
        handler.invoke(Action.Click.OnTypeToggle(ExerciseTypeUiModel.WEIGHTLESS))

        handler.invoke(Action.Click.OnTypeChangeConfirm)

        assertEquals(ExerciseTypeUiModel.WEIGHTLESS, stateFlow.value.type)
        assertNull(stateFlow.value.pendingTypeChange)
        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        assertTrue(stateFlow.value.adhocPlan?.all { it.weight == null } == true)
        // The ROWS survive — only their weights go. A switch is not a delete.
        assertEquals(2, stateFlow.value.adhocPlan?.size)
    }

    @Test
    fun `OnTypeChangeDismiss leaves both the type and the weights alone`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = null).copy(
                type = ExerciseTypeUiModel.WEIGHTED,
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
                ),
            ),
        )
        handler.invoke(Action.Click.OnTypeToggle(ExerciseTypeUiModel.WEIGHTLESS))

        handler.invoke(Action.Click.OnTypeChangeDismiss)

        assertEquals(ExerciseTypeUiModel.WEIGHTED, stateFlow.value.type)
        assertNull(stateFlow.value.pendingTypeChange)
        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        assertEquals(80.0, stateFlow.value.adhocPlan?.first()?.weight)
    }

    private data class TestSetup(
        val stateFlow: MutableStateFlow<State>,
        val store: ExerciseHandlerStore,
        val handler: ClickHandler,
    )

    /**
     * **This case is only meaningful while Save is never disabled (§26).**
     *
     * The blank name is the exact condition that produces `nameError`, so a save predicate of
     * `name.isNotBlank()` makes the state this asserts unreachable through the button — the test
     * stays green and stops measuring anything. That is B23's shape: a case whose precondition
     * production cannot produce reads as coverage on every report, and the discriminator is "ask
     * what *reaches* the state the test builds".
     */
    @Test
    fun `OnSaveClick with blank name sets nameError without saving — reachable`() {
        val (stateFlow, store, handler) = setup(State.create(uuid = null).copy(name = ""))
        handler.invoke(Action.Click.OnSaveClick)
        assertTrue(stateFlow.value.nameError)
        verify(exactly = 0) { store.sendEvent(any()) }
    }

    /** The other direction, so the gate is shown to be a gate and not an unconditional flag. */
    @Test
    fun `OnSaveClick with a name does not set nameError and proceeds`() {
        val (stateFlow, store, handler) = setup(
            State.create(uuid = null).copy(name = "Жим лёжа"),
        )
        handler.invoke(Action.Click.OnSaveClick)
        assertFalse(stateFlow.value.nameError)
        verify(exactly = 1) { store.sendEvent(Event.Haptic(HapticFeedbackType.ContextClick)) }
    }

    @Test
    fun `OnEditClick flips mode to Edit and snapshots current state`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                name = "Bench",
                type = ExerciseTypeUiModel.WEIGHTED,
                description = "Notes",
            ),
        )
        handler.invoke(Action.Click.OnEditClick)
        assertTrue(stateFlow.value.mode is Mode.Edit)
        assertEquals(false, (stateFlow.value.mode as Mode.Edit).isCreate)
        assertEquals("Bench", stateFlow.value.originalSnapshot?.name)
    }

    @Test
    fun `OnDetailMenuClick opens the detail-menu sheet`() {
        val (stateFlow, _, handler) = setup()
        handler.invoke(Action.Click.OnDetailMenuClick)
        assertEquals(BottomSheetState.DetailMenu, stateFlow.value.bottomSheetState)
    }

    @Test
    fun `OnSheetDismiss hides the detail-menu sheet`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(bottomSheetState = BottomSheetState.DetailMenu),
        )
        handler.invoke(Action.Click.OnSheetDismiss)
        assertEquals(BottomSheetState.Hidden, stateFlow.value.bottomSheetState)
    }

    @Test
    fun `OnPlanInfoClick opens the plan-info sheet`() {
        val (stateFlow, _, handler) = setup()
        handler.invoke(Action.Click.OnPlanInfoClick)
        assertEquals(BottomSheetState.PlanInfo, stateFlow.value.bottomSheetState)
    }

    @Test
    fun `OnEditClick from the sheet closes it in the same transition`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(bottomSheetState = BottomSheetState.DetailMenu),
        )
        handler.invoke(Action.Click.OnEditClick)
        assertTrue(stateFlow.value.mode is Mode.Edit)
        assertEquals(BottomSheetState.Hidden, stateFlow.value.bottomSheetState)
    }

    @Test
    fun `OnPermanentDeleteMenuClick swaps the sheet for the confirm dialog`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                canPermanentlyDelete = true,
                bottomSheetState = BottomSheetState.DetailMenu,
            ),
        )
        handler.invoke(Action.Click.OnPermanentDeleteMenuClick)
        assertTrue(stateFlow.value.dialogState is DialogState.PermanentDeleteConfirm)
        assertEquals(BottomSheetState.Hidden, stateFlow.value.bottomSheetState)
    }

    @Test
    fun `OnArchiveMenuClick closes the sheet before the archive result lands`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                name = "Bench",
                bottomSheetState = BottomSheetState.DetailMenu,
            ),
        )
        handler.invoke(Action.Click.OnArchiveMenuClick)
        assertEquals(BottomSheetState.Hidden, stateFlow.value.bottomSheetState)
    }

    /**
     * ED11's strict order at the handler seam: confirming opens the undo window and DELETES
     * NOTHING — the interactor is untouched until the window's close signal runs the model's
     * own `onDismissed`. Both directions on one flow: zero calls before, exactly one after.
     * The model is received from [SnackbarManager] itself — it rides the APP-LEVEL queue
     * from birth, because a screen-scoped event dies with the popped screen's collector
     * while carrying the commit.
     */
    @Test
    fun `confirm permanent delete defers - nothing runs until the commit`() = runTest {
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(canPermanentlyDelete = true),
        )

        handler.invoke(Action.Click.OnPermanentDeleteMenuClick)
        handler.invoke(Action.Click.OnConfirmPermanentDelete)

        coVerify(exactly = 0) { interactor.permanentlyDelete(any()) }
        verify { store.consume(Action.Navigation.Back) }

        val pending = SnackbarManager.snackbar.first()
        pending.onDismissed()
        coVerify(exactly = 1) { interactor.permanentlyDelete("uuid-1") }
    }

    @Test
    fun `minus set emits the undo toast and the undo restores the row`() {
        val plan = persistentListOf(
            PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK),
            PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
        )
        // Edit mode: the plan editor renders there alone, and the undo only applies there.
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                mode = Mode.Edit(isCreate = false),
                adhocPlan = plan,
            ),
        )
        val events = mutableListOf<Event>()
        every { store.sendEvent(capture(events)) } answers { }

        handler.invoke(
            Action.Click.OnAdhocPlanEditorAction(PlanEditorBodyAction.OnSetRemove(plan.lastIndex)),
        )
        assertEquals(1, stateFlow.value.adhocPlan?.size)
        val undo = events.filterIsInstance<Event.ShowSetRemovedUndo>().single()
        assertEquals(plan.last(), undo.set)

        handler.invoke(
            Action.Click.OnUndoSetRemove(
                set = undo.set,
                index = undo.index,
                draftEpoch = undo.draftEpoch,
            ),
        )
        assertEquals(plan, stateFlow.value.adhocPlan)
    }

    /**
     * The toast is app-level and outlives the draft (5s, accessibility-stretched), so its
     * «Отменить» can land after Save flipped to Read. The removal is persisted by then — a
     * reinserted row would sit on the Read screen with no saved row behind it. The epoch
     * guard makes the stale tap edit nothing ([State.draftEpoch]).
     */
    @Test
    fun `a stale set undo after save flipped to read edits nothing`() {
        val plan = persistentListOf(
            PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK),
        )
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                mode = Mode.Edit(isCreate = false),
                adhocPlan = plan,
            ),
        )
        val events = mutableListOf<Event>()
        every { store.sendEvent(capture(events)) } answers { }
        handler.invoke(
            Action.Click.OnAdhocPlanEditorAction(PlanEditorBodyAction.OnSetRemove(0)),
        )
        val undo = events.filterIsInstance<Event.ShowSetRemovedUndo>().single()

        // What handleSaveSuccess does on this screen: the draft ends, Read begins.
        stateFlow.value = stateFlow.value.copy(mode = Mode.Read)

        handler.invoke(
            Action.Click.OnUndoSetRemove(
                set = undo.set,
                index = undo.index,
                draftEpoch = undo.draftEpoch,
            ),
        )

        assertNull(stateFlow.value.adhocPlan)
    }

    /**
     * Save → Edit again, all inside the toast's window: the re-entered draft is a NEW one,
     * not the one the toast edited, and OnEditClick's epoch bump is what makes the stale
     * «Отменить» miss it.
     */
    @Test
    fun `a stale set undo does not edit a re-entered draft`() {
        val plan = persistentListOf(
            PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK),
        )
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                mode = Mode.Edit(isCreate = false),
                adhocPlan = plan,
            ),
        )
        val events = mutableListOf<Event>()
        every { store.sendEvent(capture(events)) } answers { }
        handler.invoke(
            Action.Click.OnAdhocPlanEditorAction(PlanEditorBodyAction.OnSetRemove(0)),
        )
        val undo = events.filterIsInstance<Event.ShowSetRemovedUndo>().single()

        stateFlow.value = stateFlow.value.copy(mode = Mode.Read) // Save's flip.
        handler.invoke(Action.Click.OnEditClick)
        assertEquals(undo.draftEpoch + 1, stateFlow.value.draftEpoch)

        handler.invoke(
            Action.Click.OnUndoSetRemove(
                set = undo.set,
                index = undo.index,
                draftEpoch = undo.draftEpoch,
            ),
        )

        assertNull(stateFlow.value.adhocPlan)
    }

    /**
     * The type-change wipe runs over rows PRESENT in the draft, and a set riding
     * its toast is absent — the undo must re-enter through the same invariant, or a
     * WEIGHTLESS exercise carries a hidden weight the DB strips and the snapshot keeps.
     * With the sole weighted row removed the draft holds no weights, so the switch is
     * immediate — no confirm sheet stands between the removal and the undo.
     */
    @Test
    fun `an undo restored across a type switch re-enters weightless`() {
        val plan = persistentListOf(
            PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK),
        )
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "00000000-0000-0000-0000-000000000001").copy(
                mode = Mode.Edit(isCreate = false),
                type = ExerciseTypeUiModel.WEIGHTED,
                adhocPlan = plan,
            ),
        )
        val events = mutableListOf<Event>()
        every { store.sendEvent(capture(events)) } answers { }
        handler.invoke(
            Action.Click.OnAdhocPlanEditorAction(PlanEditorBodyAction.OnSetRemove(0)),
        )
        val undo = events.filterIsInstance<Event.ShowSetRemovedUndo>().single()

        handler.invoke(Action.Click.OnTypeToggle(ExerciseTypeUiModel.WEIGHTLESS))
        assertEquals(ExerciseTypeUiModel.WEIGHTLESS, stateFlow.value.type)

        handler.invoke(
            Action.Click.OnUndoSetRemove(
                set = undo.set,
                index = undo.index,
                draftEpoch = undo.draftEpoch,
            ),
        )

        val restored = stateFlow.value.adhocPlan!!.single()
        assertNull(restored.weight)
        assertEquals(10, restored.reps)
    }

    /**
     * The in-flight interval: Save has captured its snapshot but the write has
     * not landed — mode is still Edit and the epoch still matches, so [State.isSaving] is
     * the only clause standing between «Отменить» and a row the database will never hold.
     * The inert `launch` mock IS the in-flight simulation: dispatched, never completed.
     */
    @Test
    fun `an undo during the save's write edits nothing`() {
        val plan = persistentListOf(
            PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK),
        )
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "00000000-0000-0000-0000-000000000001").copy(
                mode = Mode.Edit(isCreate = false),
                name = "Bench",
                adhocPlan = plan,
            ),
        )
        val events = mutableListOf<Event>()
        every { store.sendEvent(capture(events)) } answers { }
        handler.invoke(
            Action.Click.OnAdhocPlanEditorAction(PlanEditorBodyAction.OnSetRemove(0)),
        )
        val undo = events.filterIsInstance<Event.ShowSetRemovedUndo>().single()

        handler.invoke(Action.Click.OnSaveClick)
        assertTrue(stateFlow.value.isSaving)

        handler.invoke(
            Action.Click.OnUndoSetRemove(
                set = undo.set,
                index = undo.index,
                draftEpoch = undo.draftEpoch,
            ),
        )

        assertNull(stateFlow.value.adhocPlan)
    }

    /**
     * `DuplicateName` keeps the draft in Edit — the failure that re-arms its undos. The
     * `launch` mock runs the save's action and outcome synchronously; `updateStateImmediate`
     * is given a real implementation because both outcome branches land through it.
     */
    @Test
    fun `a duplicate-name save re-arms the draft's undos`() {
        val plan = persistentListOf(
            PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK),
        )
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "00000000-0000-0000-0000-000000000001").copy(
                mode = Mode.Edit(isCreate = false),
                name = "Bench",
                adhocPlan = plan,
            ),
        )
        coEvery { store.updateStateImmediate(any<suspend (State) -> State>()) } coAnswers {
            val update = firstArg<suspend (State) -> State>()
            stateFlow.value = update(stateFlow.value)
        }
        val events = mutableListOf<Event>()
        every { store.sendEvent(capture(events)) } answers { }
        handler.invoke(
            Action.Click.OnAdhocPlanEditorAction(PlanEditorBodyAction.OnSetRemove(0)),
        )
        val undo = events.filterIsInstance<Event.ShowSetRemovedUndo>().single()

        every {
            store.launch(any(), any(), any(), any(), any<suspend CoroutineScope.() -> Any?>())
        } answers {
            val onSuccess = arg<suspend CoroutineScope.(Any?) -> Unit>(1)
            val action = arg<suspend CoroutineScope.() -> Any?>(4)
            runBlocking { onSuccess(this, action()) }
            mockk(relaxed = true)
        }
        coEvery { interactor.saveExercise(any()) } returns SaveResult.DuplicateName

        handler.invoke(Action.Click.OnSaveClick)
        assertTrue(stateFlow.value.nameDuplicateError)
        assertFalse(stateFlow.value.isSaving)

        handler.invoke(
            Action.Click.OnUndoSetRemove(
                set = undo.set,
                index = undo.index,
                draftEpoch = undo.draftEpoch,
            ),
        )

        assertEquals(plan, stateFlow.value.adhocPlan)
    }

    /**
     * The same in-flight interval, the other direction: with the write dispatched, Отмена
     * may not raise the discard sheet — a rollback landing before the save's outcome
     * would leave [State.originalSnapshot] holding the saved values over reverted fields
     * ([State.isSaving]'s KDoc). The inert `launch` mock IS the in-flight simulation.
     */
    @Test
    fun `a cancel during the save's write raises nothing and reverts nothing`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "00000000-0000-0000-0000-000000000001").copy(
                mode = Mode.Edit(isCreate = false),
                type = ExerciseTypeUiModel.WEIGHTED,
                name = "Bench v2",
                originalSnapshot = State.Snapshot(
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    description = "",
                    tagUuids = emptyList(),
                    adhocPlan = null,
                ),
            ),
        )
        handler.invoke(Action.Click.OnSaveClick)
        assertTrue(stateFlow.value.isSaving)

        handler.invoke(Action.Click.OnCancelClick)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        assertTrue(stateFlow.value.mode is Mode.Edit)
        assertEquals("Bench v2", stateFlow.value.name)
    }

    /** The back gesture is the second entry to the same sheet — guarded the same way. */
    @Test
    fun `a back during the save's write raises nothing`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "00000000-0000-0000-0000-000000000001").copy(
                mode = Mode.Edit(isCreate = false),
                type = ExerciseTypeUiModel.WEIGHTED,
                name = "Bench v2",
                isSaving = true,
                originalSnapshot = State.Snapshot(
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    description = "",
                    tagUuids = emptyList(),
                    adhocPlan = null,
                ),
            ),
        )

        handler.invoke(Action.Click.OnBackClick)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        assertTrue(stateFlow.value.mode is Mode.Edit)
    }

    /**
     * The confirm is a second action after the sheet was raised, so it carries its own
     * guard — proven where only IT stands: a create's POP_SCREEN discard, which never
     * reaches the flip choke point and would double-pop under the save's own Back. (A
     * FLIP_TO_READ fixture here is vacuous — `processFlipToReadMode`'s guard masks a
     * deleted confirm guard.)
     */
    @Test
    fun `a confirmed discard during the save's write pops nothing`() {
        val (stateFlow, store, handler) = setup(
            State.create(uuid = null).copy(
                name = "Bench",
                isSaving = true,
                dialogState = DialogState.DiscardConfirm(DiscardTarget.POP_SCREEN),
            ),
        )

        handler.invoke(Action.Click.OnConfirmDiscard(DiscardTarget.POP_SCREEN))

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
    }

    /** The raw action reaches the same choke point and is refused the same way. */
    @Test
    fun `FlipToReadMode during the save's write flips nothing`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "00000000-0000-0000-0000-000000000001").copy(
                mode = Mode.Edit(isCreate = false),
                name = "Bench v2",
                isSaving = true,
            ),
        )

        handler.invoke(Action.Click.FlipToReadMode)

        assertTrue(stateFlow.value.mode is Mode.Edit)
        assertEquals("Bench v2", stateFlow.value.name)
    }

    /** A flag orphaned with a dead draft must not gag the next draft's undos. */
    @Test
    fun `Edit entry resets a stuck isSaving`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "00000000-0000-0000-0000-000000000001").copy(
                mode = Mode.Read,
                isSaving = true,
            ),
        )
        handler.invoke(Action.Click.OnEditClick)
        assertFalse(stateFlow.value.isSaving)
    }

    /**
     * The repository returns the EXISTING row for a name that already exists, so a create
     * reached with a padded already-selected name must not chip it twice — the persisted
     * links dedup on Save, and the draft must agree with them.
     */
    @Test
    fun `createTag resolving to an already-selected tag does not duplicate the chip`() {
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                tags = persistentListOf(AppTagItem(uuid = "t1", name = "Push")),
                tagSearchQuery = " Push ",
            ),
        )
        coEvery { store.updateStateImmediate(any<suspend (State) -> State>()) } coAnswers {
            val update = firstArg<suspend (State) -> State>()
            stateFlow.value = update(stateFlow.value)
        }
        every {
            store.launch(any(), any(), any(), any(), any<suspend CoroutineScope.() -> Any?>())
        } answers {
            val onSuccess = arg<suspend CoroutineScope.(Any?) -> Unit>(1)
            val action = arg<suspend CoroutineScope.() -> Any?>(4)
            runBlocking { onSuccess(this, action()) }
            mockk(relaxed = true)
        }
        coEvery { interactor.createTag("Push") } returns TagDomain(uuid = "t1", name = "Push")

        handler.invoke(Action.Click.OnTagCreate(" Push "))

        assertEquals(listOf("t1"), stateFlow.value.tags.map { it.uuid })
        assertEquals("", stateFlow.value.tagSearchQuery)
    }

    /**
     * The dispatch-time cap check can be passed twice while the first create's write is
     * still in flight, so the append re-checks the cap where the chip lands: the second
     * create resolves a row but chips nothing past [State.MAX_TAGS_PER_EXERCISE]. The
     * parked launch mock IS the in-flight interval — both creates dispatch at nine tags,
     * then their outcomes land in order.
     */
    @Test
    fun `a second create landing on a full draft chips nothing past the cap`() {
        val nineTags = (1..9).map { AppTagItem(uuid = "t$it", name = "Tag$it") }
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(tags = nineTags.toImmutableList()),
        )
        coEvery { store.updateStateImmediate(any<suspend (State) -> State>()) } coAnswers {
            val update = firstArg<suspend (State) -> State>()
            stateFlow.value = update(stateFlow.value)
        }
        val parked = mutableListOf<Pair<suspend CoroutineScope.(Any?) -> Unit, suspend CoroutineScope.() -> Any?>>()
        every {
            store.launch(any(), any(), any(), any(), any<suspend CoroutineScope.() -> Any?>())
        } answers {
            parked += arg<suspend CoroutineScope.(Any?) -> Unit>(1) to
                arg<suspend CoroutineScope.() -> Any?>(4)
            mockk(relaxed = true)
        }
        coEvery { interactor.createTag("Legs") } returns TagDomain(uuid = "t10", name = "Legs")
        coEvery { interactor.createTag("Core") } returns TagDomain(uuid = "t11", name = "Core")

        handler.invoke(Action.Click.OnTagCreate("Legs"))
        handler.invoke(Action.Click.OnTagCreate("Core"))

        parked.forEach { (onSuccess, action) -> runBlocking { onSuccess(this, action()) } }

        assertEquals(10, stateFlow.value.tags.size)
        assertEquals("t10", stateFlow.value.tags.last().uuid)
    }

    /** The toast fires on the REMOVE alone — a value edit is not a removal. */
    @Test
    fun `a plan value edit emits no undo toast`() {
        val plan = persistentListOf(
            PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK),
        )
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(adhocPlan = plan),
        )
        val events = mutableListOf<Event>()
        every { store.sendEvent(capture(events)) } answers { }

        handler.invoke(
            Action.Click.OnAdhocPlanEditorAction(PlanEditorBodyAction.OnSetWeightChange(0, 70.0)),
        )

        assertTrue(events.filterIsInstance<Event.ShowSetRemovedUndo>().isEmpty())
    }

    @Test
    fun `the dashed add chip opens the tag picker sheet`() {
        val (stateFlow, _, handler) = setup()
        handler.invoke(Action.Click.OnTagAddClick)
        assertEquals(BottomSheetState.TagPicker, stateFlow.value.bottomSheetState)
    }

    @Test
    fun `dismissing the tag picker clears the query with the sheet`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                bottomSheetState = BottomSheetState.TagPicker,
                tagSearchQuery = "кар",
            ),
        )
        handler.invoke(Action.Click.OnTagPickerDismiss)
        assertEquals(BottomSheetState.Hidden, stateFlow.value.bottomSheetState)
        assertEquals("", stateFlow.value.tagSearchQuery)
    }

    @Test
    fun `OnTagToggle adds tag when not selected`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                availableTags = persistentListOf(AppTagItem("tag-1", "Push")),
            ),
        )
        handler.invoke(Action.Click.OnTagToggle("tag-1"))
        assertEquals(listOf("tag-1"), stateFlow.value.tags.map { it.uuid })
    }

    @Test
    fun `OnTagToggle blocks adding when 10 tags already selected`() {
        val tags = (1..10).map { AppTagItem("tag-$it", "Tag$it") }
        val available = tags + AppTagItem("tag-11", "Tag11")
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                tags = persistentListOf<AppTagItem>().addAll(tags),
                availableTags = persistentListOf<AppTagItem>().addAll(available),
            ),
        )
        handler.invoke(Action.Click.OnTagToggle("tag-11"))
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertTrue(captured.captured is Event.ShowTagLimitReached)
        assertEquals(10, stateFlow.value.tags.size)
    }

    @Test
    fun `OnDismissArchiveBlocked is no-op`() {
        val (_, store, handler) = setup()
        handler.invoke(Action.Click.OnDismissArchiveBlocked)
        verify(exactly = 0) { store.sendEvent(any()) }
        verify(exactly = 0) { store.consume(any()) }
    }

    @Test
    fun `OnTrackNowClick emits ContextClick haptic`() {
        val (_, store, handler) = setup()
        handler.invoke(Action.Click.OnTrackNowClick)
        val events = mutableListOf<Event>()
        verify { store.sendEvent(capture(events)) }
        assertTrue(events.any { it is Event.Haptic && it.type == HapticFeedbackType.ContextClick })
    }

    @Test
    fun `OnTrackNowClick reports the persisted active session progress`() {
        val (stateFlow, store, handler) = setup(State.create(uuid = "exercise-1"))
        coEvery { store.updateStateImmediate(any<suspend (State) -> State>()) } coAnswers {
            val update = firstArg<suspend (State) -> State>()
            stateFlow.value = update(stateFlow.value)
        }
        every {
            store.launch(any(), any(), any(), any(), any<suspend CoroutineScope.() -> Any?>())
        } answers {
            val action = arg<suspend CoroutineScope.() -> Any?>(4)
            runBlocking { action() }
            mockk(relaxed = true)
        }
        coEvery { interactor.resolveTrackNowConflict() } returns TrackNowConflict.NeedsUserChoice(
            active = ActiveSessionDomain(
                sessionUuid = "active-1",
                trainingUuid = "training-1",
                startedAt = 1L,
            ),
            trainingName = "Push Day",
            doneCount = 1,
            totalCount = 2,
        )
        every {
            resourceWrapper.getString(
                io.github.stslex.workeeper.feature.exercise.R.string
                    .feature_exercise_track_now_conflict_progress_format,
                1,
                2,
            )
        } returns "1 of 2 exercises done"

        handler.invoke(Action.Click.OnTrackNowClick)

        val dialog = stateFlow.value.dialogState as DialogState.ActiveSessionConflict
        assertEquals("1 of 2 exercises done", dialog.progressLabel)
    }

    @Test
    fun `OnTrackNowResumeConfirm with no pending conflict is a no-op`() {
        val (_, store, handler) = setup()
        handler.invoke(Action.Click.OnTrackNowResumeConfirm)
        verify(exactly = 0) { store.consume(any<Action.Navigation.OpenLiveWorkout>()) }
    }

    @Test
    fun `OnTrackNowResumeConfirm consumes OpenLiveWorkout with active session uuid`() {
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                dialogState = DialogState.ActiveSessionConflict(
                    sessionUuid = "active-1",
                    activeSessionName = "Push Day",
                    progressLabel = "0 of 0",
                ),
            ),
        )
        handler.invoke(Action.Click.OnTrackNowResumeConfirm)
        verify { store.consume(Action.Navigation.OpenLiveWorkout("active-1")) }
    }

    @Test
    fun `OnTrackNowConflictDismiss clears the active session conflict dialog`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                dialogState = DialogState.ActiveSessionConflict(
                    sessionUuid = "active-1",
                    activeSessionName = "Push Day",
                    progressLabel = "0 of 0",
                ),
            ),
        )
        handler.invoke(Action.Click.OnTrackNowConflictDismiss)
        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    @Test
    fun `OnCancelClick from clean Edit on existing flips to Read mode`() {
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(mode = Mode.Edit(isCreate = false)),
        )
        handler.invoke(Action.Click.OnCancelClick)
        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
        assertEquals(Mode.Read, stateFlow.value.mode)
    }

    @Test
    fun `OnCancelClick from clean create mode pops back`() {
        val (_, store, handler) = setup(
            State.create(uuid = null),
        )
        handler.invoke(Action.Click.OnCancelClick)
        verify { store.consume(Action.Navigation.Back) }
    }

    @Test
    fun `OnCancelClick from dirty Edit on existing shows FLIP_TO_READ discard dialog`() {
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                mode = Mode.Edit(isCreate = false),
                name = "Bench updated",
                originalSnapshot = State.Snapshot(
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    description = "",
                    tagUuids = emptyList(),
                    adhocPlan = null,
                ),
            ),
        )
        handler.invoke(Action.Click.OnCancelClick)
        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
        val dialog = stateFlow.value.dialogState
        assertTrue(dialog is DialogState.DiscardConfirm)
        assertEquals(DiscardTarget.FLIP_TO_READ, (dialog as DialogState.DiscardConfirm).target)
    }

    @Test
    fun `OnConfirmDiscard with POP_SCREEN navigates back`() {
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(mode = Mode.Edit(isCreate = false)),
        )
        handler.invoke(Action.Click.OnConfirmDiscard(DiscardTarget.POP_SCREEN))
        verify { store.consume(Action.Navigation.Back) }
    }

    @Test
    fun `OnConfirmDiscard with FLIP_TO_READ flips mode without popping`() {
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                mode = Mode.Edit(isCreate = false),
                name = "Bench edited",
                originalSnapshot = State.Snapshot(
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    description = "",
                    tagUuids = emptyList(),
                    adhocPlan = null,
                ),
            ),
        )
        handler.invoke(Action.Click.OnConfirmDiscard(DiscardTarget.FLIP_TO_READ))
        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
        assertEquals(Mode.Read, stateFlow.value.mode)
        assertEquals("Bench", stateFlow.value.name)
    }

    /**
     * The plan is the field the discard sheet is usually ABOUT — since ED1 it is edited inline,
     * and `Snapshot.matches` counts it when raising the sheet. Discarding must put it back.
     */
    @Test
    fun `OnConfirmDiscard with FLIP_TO_READ restores the plan the edit changed`() {
        val original = persistentListOf(
            PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
        )
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                mode = Mode.Edit(isCreate = false),
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = 100.0, reps = 3, type = SetTypeUiModel.WORK),
                    PlanSetUiModel(weight = 100.0, reps = 3, type = SetTypeUiModel.WORK),
                ),
                originalSnapshot = State.Snapshot(
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    description = "",
                    tagUuids = emptyList(),
                    adhocPlan = original,
                ),
            ),
        )

        handler.invoke(Action.Click.OnConfirmDiscard(DiscardTarget.FLIP_TO_READ))

        assertEquals(original, stateFlow.value.adhocPlan)
    }

    /** A plan that was empty before the edit comes back empty, not merely unchanged. */
    @Test
    fun `OnConfirmDiscard with FLIP_TO_READ restores a plan that was absent`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                mode = Mode.Edit(isCreate = false),
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = 100.0, reps = 3, type = SetTypeUiModel.WORK),
                ),
                originalSnapshot = State.Snapshot(
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    description = "",
                    tagUuids = emptyList(),
                    adhocPlan = null,
                ),
            ),
        )

        handler.invoke(Action.Click.OnConfirmDiscard(DiscardTarget.FLIP_TO_READ))

        assertNull(stateFlow.value.adhocPlan)
    }

    @Test
    fun `OnBackClick in clean Edit on existing flips to Read mode`() {
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(mode = Mode.Edit(isCreate = false)),
        )
        handler.invoke(Action.Click.OnBackClick)
        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
        assertEquals(Mode.Read, stateFlow.value.mode)
    }

    @Test
    fun `OnPermanentDeleteMenuClick is no-op when not eligible`() {
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(canPermanentlyDelete = false),
        )
        handler.invoke(Action.Click.OnPermanentDeleteMenuClick)
        verify(exactly = 0) { store.sendEvent(any()) }
    }

    @Test
    fun `OnPermanentDeleteMenuClick surfaces the PermanentDeleteConfirm dialog when eligible`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                canPermanentlyDelete = true,
                name = "Bench",
            ),
        )
        handler.invoke(Action.Click.OnPermanentDeleteMenuClick)
        assertTrue(stateFlow.value.dialogState is DialogState.PermanentDeleteConfirm)
    }

    @Test
    fun `OnEditImageClick opens the image source picker dialog`() {
        val (stateFlow, _, handler) = setup()
        handler.invoke(Action.Click.OnEditImageClick)
        assertEquals(DialogState.ImageSourcePicker, stateFlow.value.dialogState)
    }

    @Test
    fun `OnRemoveImageClick stages a RemoveExisting pending image`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                imagePath = "/files/old.jpg",
                imageLastModified = 100L,
            ),
        )
        handler.invoke(Action.Click.OnRemoveImageClick)
        assertEquals(
            PendingImage.RemoveExisting,
            stateFlow.value.pendingImage,
        )
    }

    @Test
    fun `saving an image removal clears the persisted path before deleting the old file`() {
        val uuid = "00000000-0000-0000-0000-000000000001"
        val oldPath = "/files/old.jpg"
        val (stateFlow, store, handler) = setup(
            State.create(uuid = uuid).copy(
                mode = Mode.Edit(isCreate = false),
                name = "Bench",
                imagePath = oldPath,
                pendingImage = PendingImage.RemoveExisting,
                originalSnapshot = State.Snapshot(
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    description = "",
                    tagUuids = emptyList(),
                    adhocPlan = null,
                ),
            ),
        )
        wireSynchronousLaunch(store, stateFlow)
        val saved = slot<ExerciseChangeDomain>()
        coEvery { interactor.saveExercise(capture(saved)) } returns
            SaveResult.Success(kotlin.uuid.Uuid.parse(uuid))

        handler.invoke(Action.Click.OnSaveClick)

        assertNull(saved.captured.imagePath)
        coVerifyOrder {
            interactor.saveExercise(any())
            interactor.deleteImageFile(oldPath)
        }
        assertEquals(Mode.Read, stateFlow.value.mode)
        assertNull(stateFlow.value.imagePath)
        assertEquals(PendingImage.Unchanged, stateFlow.value.pendingImage)
    }

    @Test
    fun `a rejected image removal keeps the attachment file and the staged draft`() {
        val oldPath = "/files/old.jpg"
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "00000000-0000-0000-0000-000000000001").copy(
                mode = Mode.Edit(isCreate = false),
                name = "Bench",
                imagePath = oldPath,
                pendingImage = PendingImage.RemoveExisting,
                originalSnapshot = State.Snapshot(
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    description = "",
                    tagUuids = emptyList(),
                    adhocPlan = null,
                ),
            ),
        )
        wireSynchronousLaunch(store, stateFlow)
        coEvery { interactor.saveExercise(any()) } returns SaveResult.DuplicateName

        handler.invoke(Action.Click.OnSaveClick)

        assertTrue(stateFlow.value.mode is Mode.Edit)
        assertEquals(PendingImage.RemoveExisting, stateFlow.value.pendingImage)
        assertEquals(ImageDisplay.None, stateFlow.value.effectiveImageDisplay)
        coVerify(exactly = 0) { interactor.deleteImageFile(any()) }
    }

    @Test
    fun `cancelling an image removal restores the attachment without deleting its file`() {
        val oldPath = "/files/old.jpg"
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                mode = Mode.Edit(isCreate = false),
                name = "Bench",
                imagePath = oldPath,
                imageLastModified = 100L,
                pendingImage = PendingImage.RemoveExisting,
                originalSnapshot = State.Snapshot(
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    description = "",
                    tagUuids = emptyList(),
                    adhocPlan = null,
                ),
            ),
        )

        handler.invoke(Action.Click.OnCancelClick)
        assertEquals(
            DialogState.DiscardConfirm(DiscardTarget.FLIP_TO_READ),
            stateFlow.value.dialogState,
        )

        handler.invoke(Action.Click.OnConfirmDiscard(DiscardTarget.FLIP_TO_READ))

        assertEquals(Mode.Read, stateFlow.value.mode)
        assertEquals(PendingImage.Unchanged, stateFlow.value.pendingImage)
        assertEquals(ImageDisplay.FromPath(oldPath, 100L), stateFlow.value.effectiveImageDisplay)
        coVerify(exactly = 0) { interactor.deleteImageFile(any()) }
    }

    @Test
    fun `OnImageSourceDialogDismiss hides the source dialog`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(dialogState = DialogState.ImageSourcePicker),
        )
        handler.invoke(Action.Click.OnImageSourceDialogDismiss)
        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    @Test
    fun `OnPermissionDeniedDialogDismiss hides the permission dialog`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(dialogState = DialogState.PermissionDenied),
        )
        handler.invoke(Action.Click.OnPermissionDeniedDialogDismiss)
        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    @Test
    fun `OnCameraPermissionDenied surfaces the permission denied dialog`() {
        val (stateFlow, _, handler) = setup()
        handler.invoke(Action.Click.OnCameraPermissionDenied)
        assertEquals(DialogState.PermissionDenied, stateFlow.value.dialogState)
    }

    @Test
    fun `OnPermissionDeniedSettingsClick emits NavigateOpenAppSettings`() {
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(dialogState = DialogState.PermissionDenied),
        )
        handler.invoke(Action.Click.OnPermissionDeniedSettingsClick)
        val events = mutableListOf<Event>()
        verify { store.sendEvent(capture(events)) }
        assertTrue(events.any { it is Event.NavigateOpenAppSettings })
    }

    @Test
    fun `OnImageThumbnailClick with committed path consumes OpenImageViewer with the path`() {
        val path = "/data/user/0/app/files/exercise_images/uuid-1.jpg"
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                imagePath = path,
                imageLastModified = 100L,
            ),
        )
        handler.invoke(Action.Click.OnImageThumbnailClick)
        verify { store.consume(Action.Navigation.OpenImageViewer(path, editable = false)) }
    }

    /**
     * The viewer carries replace and remove, and only a caller that can honour one may be offered
     * it. Read mode cannot — no Save, and `interceptBack` is false there — so a replace staged
     * from the detail hero would look applied and be lost on the way out. The capability is stated
     * on the route by the caller rather than guessed at by the viewer.
     */
    @Test
    fun `Edit mode opens the viewer as editable`() {
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                mode = Mode.Edit(isCreate = false),
                imagePath = "/img/a.png",
            ),
        )

        handler.invoke(Action.Click.OnImageThumbnailClick)

        verify(exactly = 1) {
            store.consume(
                Action.Navigation.OpenImageViewer(model = "/img/a.png", editable = true),
            )
        }
    }

    @Test
    fun `Read mode opens the viewer as NOT editable`() {
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                mode = Mode.Read,
                imagePath = "/img/a.png",
            ),
        )

        handler.invoke(Action.Click.OnImageThumbnailClick)

        verify(exactly = 1) {
            store.consume(
                Action.Navigation.OpenImageViewer(model = "/img/a.png", editable = false),
            )
        }
    }

    @Test
    fun `OnImageThumbnailClick with no image is a no-op`() {
        val (_, store, handler) = setup()
        handler.invoke(Action.Click.OnImageThumbnailClick)
        verify(exactly = 0) { store.consume(any<Action.Navigation.OpenImageViewer>()) }
        verify(exactly = 0) { store.sendEvent(any()) }
    }

    @Test
    fun `OnAdhocPlanEditorAction OnAddSet appends a default set to the in-memory plan`() {
        val (stateFlow, _, handler) = setup(State.create(uuid = null))

        handler.invoke(
            Action.Click.OnAdhocPlanEditorAction(PlanEditorBodyAction.OnAddSet),
        )

        val plan = stateFlow.value.adhocPlan
        assertEquals(1, plan?.size)
        assertEquals(SetTypeUiModel.WORK, plan?.first()?.type)
    }

    @Test
    fun `OnAdhocPlanEditorAction OnSetRemove on the only row normalizes the plan back to null`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = null).copy(
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
                ),
            ),
        )

        handler.invoke(
            Action.Click.OnAdhocPlanEditorAction(PlanEditorBodyAction.OnSetRemove(0)),
        )

        // Empty draft is normalized to null so `state.adhocPlan == null` continues to mean
        // "no default plan attached" — matches the persisted shape on `last_adhoc_sets`.
        assertNull(stateFlow.value.adhocPlan)
    }

    @Test
    fun `OnAdhocPlanEditorAction OnSetWeightChange routes through the reducer`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = null).copy(
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
                ),
            ),
        )

        handler.invoke(
            Action.Click.OnAdhocPlanEditorAction(
                PlanEditorBodyAction.OnSetWeightChange(index = 0, value = 95.0),
            ),
        )

        assertEquals(95.0, stateFlow.value.adhocPlan?.first()?.weight)
    }

    @Test
    fun `OnAdhocPlanEditorAction OnSetRepsChange routes through the reducer`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = null).copy(
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = null, reps = 5, type = SetTypeUiModel.WORK),
                ),
            ),
        )

        handler.invoke(
            Action.Click.OnAdhocPlanEditorAction(
                PlanEditorBodyAction.OnSetRepsChange(index = 0, value = 12),
            ),
        )

        assertEquals(12, stateFlow.value.adhocPlan?.first()?.reps)
    }

    @Test
    fun `OnAdhocPlanEditorAction OnSetTypeChange routes through the reducer`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = null).copy(
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
                ),
            ),
        )

        handler.invoke(
            Action.Click.OnAdhocPlanEditorAction(
                PlanEditorBodyAction.OnSetTypeChange(
                    index = 0,
                    value = SetTypeUiModel.FAILURE,
                ),
            ),
        )

        assertEquals(SetTypeUiModel.FAILURE, stateFlow.value.adhocPlan?.first()?.type)
    }

    @Test
    fun `OnCancelClick from create-mode with edited plan surfaces POP_SCREEN discard dialog`() {
        val (_, store, handler) = setup(
            State.create(uuid = null).copy(
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
                ),
            ),
        )

        handler.invoke(Action.Click.OnCancelClick)

        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
        val events = mutableListOf<Event>()
        verify { store.sendEvent(capture(events)) }
        assertTrue((store.state.value.dialogState as? DialogState.DiscardConfirm)?.target == DiscardTarget.POP_SCREEN)
    }

    @Test
    fun `OnBackClick from create-mode with edited plan surfaces POP_SCREEN discard dialog`() {
        val (_, store, handler) = setup(
            State.create(uuid = null).copy(
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
                ),
            ),
        )

        handler.invoke(Action.Click.OnBackClick)

        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
        val events = mutableListOf<Event>()
        verify { store.sendEvent(capture(events)) }
        assertTrue((store.state.value.dialogState as? DialogState.DiscardConfirm)?.target == DiscardTarget.POP_SCREEN)
    }
}
