// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.PickerExercise
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.store.DialogState
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ClickHandlerTest {

    private val interactor = mockk<SingleTrainingInteractor>(relaxed = true)
    private val initialState = State.create(uuid = null)
    private val stateFlow = MutableStateFlow(initialState)

    private val store = mockk<SingleTrainingHandlerStore>(relaxed = true).apply {
        every { state } returns stateFlow
        every { updateState(any()) } answers {
            val update = firstArg<(State) -> State>()
            stateFlow.value = update(stateFlow.value)
        }
        coEvery { updateStateImmediate(any<suspend (State) -> State>()) } coAnswers {
            val update = firstArg<suspend (State) -> State>()
            stateFlow.value = update(stateFlow.value)
        }
        every {
            launch(
                any(),
                any(),
                any(),
                any(),
                any<suspend CoroutineScope.() -> Unit>(),
            )
        } answers {
            mockk(relaxed = true)
        }
    }

    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)

    private val handler = ClickHandler(interactor, resourceWrapper, Dispatchers.Unconfined, store)

    /**
     * **These two are only meaningful while Save is never disabled (§26).**
     *
     * A save predicate on this screen would be `name.isNotBlank() && exercises.isNotEmpty()`, and
     * it hides **two** branches, not one: the first conjunct is the exact condition that produces
     * `nameError`, the second is the one that emits `ShowSaveError`. Gate the button on either and
     * the matching case here stays green while measuring a state no user can reach — B23's shape,
     * twice. The discriminator is "ask what *reaches* the state the test builds".
     */
    @Test
    fun `OnSaveClick with blank name flips nameError — reachable`() {
        stateFlow.value = stateFlow.value.copy(name = "")
        handler.invoke(Action.Click.OnSaveClick)
        assertTrue(stateFlow.value.nameError)
    }

    @Test
    fun `OnSaveClick with empty exercises emits ShowSaveError — reachable`() {
        stateFlow.value = stateFlow.value.copy(name = "Push Day")
        handler.invoke(Action.Click.OnSaveClick)
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertTrue(captured.captured is Event.ShowSaveError)
    }

    /**
     * The other direction, so both branches are shown to be gates rather than unconditional
     * flags: a named training with an exercise in it sets no error and emits no save error.
     */
    @Test
    fun `OnSaveClick with a name and exercises raises neither error`() {
        stateFlow.value = stateFlow.value.copy(
            name = "Push Day",
            exercises = persistentListOf(
                TrainingExerciseItem(
                    exerciseUuid = "ex-1",
                    exerciseName = "Жим лёжа",
                    exerciseType = ExerciseTypeUiModel.WEIGHTED,
                    tags = persistentListOf(),
                    position = 0,
                    planSets = null,
                    planSummary = "",
                ),
            ),
        )

        handler.invoke(Action.Click.OnSaveClick)

        assertFalse(stateFlow.value.nameError)
        verify(exactly = 0) { store.sendEvent(any<Event.ShowSaveError>()) }
    }

    @Test
    fun `OnEditClick flips into Edit mode`() {
        stateFlow.value = stateFlow.value.copy(mode = State.Mode.Read)
        handler.invoke(Action.Click.OnEditClick)
        assertTrue(stateFlow.value.mode is State.Mode.Edit)
    }

    /**
     * D-OPEN-8: an insert is an addressed gesture whose next step is the plan, so the inserted
     * card opens — and a multi-insert opens the FIRST only. The `launch` mock executes the
     * action and its onSuccess synchronously, because the ruling lives inside them.
     */
    @Test
    fun `OnPickerConfirm opens the first inserted card only`() {
        every {
            store.launch(any(), any(), any(), any(), any<suspend CoroutineScope.() -> Any?>())
        } answers {
            val onSuccess = arg<suspend CoroutineScope.(Any?) -> Unit>(1)
            val action = arg<suspend CoroutineScope.() -> Any?>(4)
            runBlocking { onSuccess(this, action()) }
            mockk(relaxed = true)
        }
        coEvery { interactor.resolveExercises(listOf("ex-1", "ex-2")) } returns listOf(
            PickerExercise(
                exercise = ExerciseDomain("ex-1", "Bench", ExerciseTypeDomain.WEIGHTED, null, null),
                labels = emptyList(),
            ),
            PickerExercise(
                exercise = ExerciseDomain("ex-2", "Row", ExerciseTypeDomain.WEIGHTED, null, null),
                labels = emptyList(),
            ),
        )
        stateFlow.value = stateFlow.value.copy(
            pickerState = State.PickerState.Open(
                query = "",
                results = persistentListOf(),
                selectedUuids = persistentListOf("ex-1", "ex-2"),
            ),
        )

        handler.invoke(Action.Click.OnPickerConfirm)

        assertEquals(2, stateFlow.value.exercises.size)
        // The FIRST inserted opens (D-OPEN-8); the second does not.
        assertTrue("ex-1" in stateFlow.value.expandedExerciseUuids)
        assertTrue("ex-2" !in stateFlow.value.expandedExerciseUuids)
        // Inserted with NO plan: null, not an empty list — the persisted shape.
        assertEquals(null, stateFlow.value.exercises.first().planSets)
    }

    /** ED14: collapsed is the INITIAL state — the tap opens the tapped card. */
    @Test
    fun `OnExerciseCardToggle expands the tapped card`() {
        stateFlow.value = stateFlow.value.copy(exercises = persistentListOf(exercise("ex-1")))
        handler.invoke(Action.Click.OnExerciseCardToggle("ex-1"))
        assertTrue("ex-1" in stateFlow.value.expandedExerciseUuids)
    }

    @Test
    fun `OnExerciseCardToggle on the open card collapses it`() {
        stateFlow.value = stateFlow.value.copy(
            exercises = persistentListOf(exercise("ex-1")),
            expandedExerciseUuids = persistentSetOf("ex-1"),
        )
        handler.invoke(Action.Click.OnExerciseCardToggle("ex-1"))
        assertTrue(stateFlow.value.expandedExerciseUuids.isEmpty())
    }

    /**
     * ED14's amendment: expansion is PER CARD, never an accordion — opening the second
     * card must not collapse the first, or the page shifts under the finger mid-edit.
     */
    @Test
    fun `cards expand independently`() {
        stateFlow.value = stateFlow.value.copy(
            exercises = persistentListOf(exercise("ex-1"), exercise("ex-2", position = 1)),
            expandedExerciseUuids = persistentSetOf("ex-1"),
        )
        handler.invoke(Action.Click.OnExerciseCardToggle("ex-2"))
        assertTrue("ex-1" in stateFlow.value.expandedExerciseUuids)
        assertTrue("ex-2" in stateFlow.value.expandedExerciseUuids)
    }

    /** ED1 on this screen: the plan reduces in memory, against the addressed exercise only. */
    @Test
    fun `OnExercisePlanAction adds a set to the addressed exercise and refreshes its summary`() {
        stateFlow.value = stateFlow.value.copy(
            exercises = persistentListOf(exercise("ex-1"), exercise("ex-2", position = 1)),
        )
        handler.invoke(
            Action.Click.OnExercisePlanAction("ex-1", PlanEditorBodyAction.OnAddSet),
        )
        val first = stateFlow.value.exercises.first()
        val second = stateFlow.value.exercises.last()
        assertEquals(1, first.planSets?.size)
        assertEquals(null, second.planSets)
    }

    /**
     * Removing the last set normalizes the plan back to NULL, not an empty list —
     * `plan_sets IS NULL` is attached-with-no-plan, the persisted shape, and the dirty
     * signature compares this value (a lingering `[]` would read dirty forever).
     */
    @Test
    fun `OnExercisePlanAction removing the last set normalizes to null`() {
        stateFlow.value = stateFlow.value.copy(
            exercises = persistentListOf(
                exercise("ex-1").copy(
                    planSets = persistentListOf(
                        PlanSetUiModel(weight = 80.0, reps = 5, type = SetTypeUiModel.WORK),
                    ),
                    planSummary = "80×5",
                ),
            ),
        )
        handler.invoke(
            Action.Click.OnExercisePlanAction("ex-1", PlanEditorBodyAction.OnSetRemove(0)),
        )
        val item = stateFlow.value.exercises.first()
        assertEquals(null, item.planSets)
        assertEquals("", item.planSummary)
    }

    /** A plan edit is an unsaved change: back must raise the discard sheet over it. */
    @Test
    fun `a plan edit flips hasChanges through the snapshot signature`() {
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            name = "Push Day",
            exercises = persistentListOf(exercise("ex-1")),
        )
        stateFlow.value = stateFlow.value.copy(
            originalSnapshot = stateFlow.value.toSnapshot(),
        )
        assertFalse(stateFlow.value.hasChanges)

        handler.invoke(
            Action.Click.OnExercisePlanAction("ex-1", PlanEditorBodyAction.OnAddSet),
        )

        assertTrue(stateFlow.value.hasChanges)
    }

    @Test
    fun `OnExerciseRemove removes the exercise and reindexes positions`() {
        stateFlow.value = stateFlow.value.copy(
            exercises = persistentListOf(
                TrainingExerciseItem(
                    exerciseUuid = "ex-1",
                    exerciseName = "Bench",
                    exerciseType = ExerciseTypeUiModel.WEIGHTED,
                    tags = persistentListOf(),
                    position = 0,
                    planSets = null,
                    planSummary = "",
                ),
                TrainingExerciseItem(
                    exerciseUuid = "ex-2",
                    exerciseName = "Squat",
                    exerciseType = ExerciseTypeUiModel.WEIGHTED,
                    tags = persistentListOf(),
                    position = 1,
                    planSets = null,
                    planSummary = "",
                ),
            ),
        )
        handler.invoke(Action.Click.OnExerciseRemove("ex-1"))
        val remaining = stateFlow.value.exercises
        assertEquals(1, remaining.size)
        assertEquals("ex-2", remaining[0].exerciseUuid)
        assertEquals(0, remaining[0].position)
    }

    @Test
    fun `OnExerciseReorder swaps positions on a non-trivial move`() {
        stateFlow.value = stateFlow.value.copy(
            exercises = persistentListOf(
                TrainingExerciseItem(
                    exerciseUuid = "ex-1",
                    exerciseName = "Bench",
                    exerciseType = ExerciseTypeUiModel.WEIGHTED,
                    tags = persistentListOf(),
                    position = 0,
                    planSets = null,
                    planSummary = "",
                ),
                TrainingExerciseItem(
                    exerciseUuid = "ex-2",
                    exerciseName = "Squat",
                    exerciseType = ExerciseTypeUiModel.WEIGHTED,
                    tags = persistentListOf(),
                    position = 1,
                    planSets = null,
                    planSummary = "",
                ),
            ),
        )
        handler.invoke(Action.Click.OnExerciseReorder(from = 0, to = 1))
        val ordered = stateFlow.value.exercises
        assertEquals("ex-2", ordered[0].exerciseUuid)
        assertEquals(0, ordered[0].position)
        assertEquals("ex-1", ordered[1].exerciseUuid)
        assertEquals(1, ordered[1].position)
    }

    @Test
    fun `OnExerciseReorder with from equal to to is a no-op`() {
        val items = persistentListOf(
            TrainingExerciseItem(
                exerciseUuid = "ex-1",
                exerciseName = "Bench",
                exerciseType = ExerciseTypeUiModel.WEIGHTED,
                tags = persistentListOf(),
                position = 0,
                planSets = null,
                planSummary = "",
            ),
        )
        stateFlow.value = stateFlow.value.copy(exercises = items)
        handler.invoke(Action.Click.OnExerciseReorder(from = 0, to = 0))
        assertEquals(items, stateFlow.value.exercises)
    }

    @Test
    fun `OnAddExerciseClick emits haptic`() {
        handler.invoke(Action.Click.OnAddExerciseClick)
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertTrue(captured.captured is Event.HapticClick)
        assertEquals(HapticFeedbackType.ContextClick, (captured.captured as Event.HapticClick).type)
    }

    @Test
    fun `OnConflictDismiss clears the active session conflict dialog`() {
        stateFlow.value = stateFlow.value.copy(
            dialogState = DialogState.ActiveSessionConflict(
                sessionUuid = "session-1",
                activeSessionName = "Push Day",
                progressLabel = "0 of 0",
            ),
        )
        handler.invoke(Action.Click.OnConflictDismiss)
        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    @Test
    fun `OnConflictResume consumes OpenLiveWorkout with active session uuid`() {
        stateFlow.value = stateFlow.value.copy(
            dialogState = DialogState.ActiveSessionConflict(
                sessionUuid = "session-1",
                activeSessionName = "Push Day",
                progressLabel = "0 of 0",
            ),
        )
        handler.invoke(Action.Click.OnConflictResume)
        verify {
            store.consume(
                Action.Navigation.OpenLiveWorkout(
                    sessionUuid = "session-1",
                    trainingUuid = null,
                ),
            )
        }
        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    @Test
    fun `OnBackClick closes the dialog before propagating to navigation`() {
        stateFlow.value = stateFlow.value.copy(
            dialogState = DialogState.ActiveSessionConflict(
                sessionUuid = "session-1",
                activeSessionName = "Push Day",
                progressLabel = "0 of 0",
            ),
        )
        handler.invoke(Action.Click.OnBackClick)
        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        verify(exactly = 0) { store.consume(any<Action.Navigation.Back>()) }
    }

    /**
     * ED14 governs ENTERING the editor, and a save is not the only way back to Read — so the
     * whole reported path is walked here rather than the one exit: expand, save, tap Edit. Between
     * the save and the tap the set is invisible (Read renders `TrainingExerciseRow`, not the
     * card), which is why nothing catches this without re-entering.
     */
    @Test
    fun `entering the editor after a save collapses every card`() {
        every {
            store.launch(any(), any(), any(), any(), any<suspend CoroutineScope.() -> Any?>())
        } answers {
            val onSuccess = arg<suspend CoroutineScope.(Any?) -> Unit>(1)
            val action = arg<suspend CoroutineScope.() -> Any?>(4)
            runBlocking { onSuccess(this, action()) }
            mockk(relaxed = true)
        }
        stateFlow.value = stateFlow.value.copy(
            uuid = "training-1",
            mode = State.Mode.Edit(isCreate = false),
            name = "Push Day",
            exercises = persistentListOf(exercise("ex-1", 0), exercise("ex-2", 1)),
        )
        handler.invoke(Action.Click.OnExerciseCardToggle("ex-1"))
        handler.invoke(Action.Click.OnExerciseCardToggle("ex-2"))
        assertEquals(2, stateFlow.value.expandedExerciseUuids.size)

        handler.invoke(Action.Click.OnSaveClick)
        assertEquals(State.Mode.Read, stateFlow.value.mode)

        handler.invoke(Action.Click.OnEditClick)

        assertTrue(stateFlow.value.expandedExerciseUuids.isEmpty())
    }

    /**
     * The snapshot stores whole items; the comparison is narrower than the storage, over
     * uuid + position + plan. An exercise renamed on its own screen and refreshed into this list
     * is not an unsaved edit to THIS training, and must not raise the discard sheet.
     */
    @Test
    fun `a refreshed exercise name is not an unsaved change`() {
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            exercises = persistentListOf(exercise("ex-1")),
        )
        stateFlow.value = stateFlow.value.copy(originalSnapshot = stateFlow.value.toSnapshot())

        stateFlow.value = stateFlow.value.copy(
            exercises = persistentListOf(
                stateFlow.value.exercises.first().copy(exerciseName = "Жим лёжа, узкий хват"),
            ),
        )

        assertFalse(stateFlow.value.hasChanges)
    }

    /**
     * Three things the snapshot has to be able to put back. They are one test each because they
     * fail separately: rebuilding the list from the current one restores an order and nothing
     * else, and only the whole-list restore satisfies all three at once.
     */
    @Test
    fun `OnConfirmDiscard restores an exercise the edit removed`() {
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            exercises = persistentListOf(exercise("ex-1", 0), exercise("ex-2", 1)),
        )
        stateFlow.value = stateFlow.value.copy(originalSnapshot = stateFlow.value.toSnapshot())
        handler.invoke(Action.Click.OnExerciseRemove("ex-1"))
        assertEquals(1, stateFlow.value.exercises.size)

        handler.invoke(Action.Click.OnConfirmDiscard)

        assertEquals(
            listOf("ex-1", "ex-2"),
            stateFlow.value.exercises.map { it.exerciseUuid },
        )
    }

    @Test
    fun `OnConfirmDiscard restores the positions a reorder changed`() {
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            exercises = persistentListOf(exercise("ex-1", 0), exercise("ex-2", 1)),
        )
        stateFlow.value = stateFlow.value.copy(originalSnapshot = stateFlow.value.toSnapshot())
        handler.invoke(Action.Click.OnExerciseReorder(0, 1))
        assertEquals(listOf("ex-2", "ex-1"), stateFlow.value.exercises.map { it.exerciseUuid })

        handler.invoke(Action.Click.OnConfirmDiscard)

        // Both the ORDER and the `position` field, which the read screen renders as "N.".
        assertEquals(listOf("ex-1", "ex-2"), stateFlow.value.exercises.map { it.exerciseUuid })
        assertEquals(listOf(0, 1), stateFlow.value.exercises.map { it.position })
    }

    @Test
    fun `OnConfirmDiscard restores the plan a card edited`() {
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            exercises = persistentListOf(exercise("ex-1")),
        )
        stateFlow.value = stateFlow.value.copy(originalSnapshot = stateFlow.value.toSnapshot())
        handler.invoke(
            Action.Click.OnExercisePlanAction("ex-1", PlanEditorBodyAction.OnAddSet),
        )
        assertTrue(stateFlow.value.hasChanges)

        handler.invoke(Action.Click.OnConfirmDiscard)

        assertEquals(null, stateFlow.value.exercises.first().planSets)
        assertFalse(stateFlow.value.hasChanges)
    }

    @Test
    fun `the topbar menu opens as the DetailMenu sheet`() {
        handler.invoke(Action.Click.OnDetailMenuClick)

        assertEquals(DialogState.DetailMenu, stateFlow.value.dialogState)
    }

    @Test
    fun `dismissing the menu hides it`() {
        stateFlow.value = stateFlow.value.copy(dialogState = DialogState.DetailMenu)

        handler.invoke(Action.Click.OnDetailMenuDismiss)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    @Test
    fun `archive closes the menu before its result lands`() {
        stateFlow.value = stateFlow.value.copy(
            uuid = "training-1",
            dialogState = DialogState.DetailMenu,
        )

        handler.invoke(Action.Click.OnArchiveClick)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    /**
     * One sealed field means the menu and the confirm cannot be open at once: the confirm's
     * write REPLACES the menu variant in the same transition (Rule 4 of
     * compose-state-discipline; the exercise feature carries the same property across two
     * fields, this one carries it by construction).
     */
    @Test
    fun `permanent delete replaces the menu with its confirm in one write`() {
        stateFlow.value = stateFlow.value.copy(
            uuid = "training-1",
            canPermanentlyDelete = true,
            dialogState = DialogState.DetailMenu,
        )

        handler.invoke(Action.Click.OnPermanentDeleteClick)

        assertTrue(stateFlow.value.dialogState is DialogState.PermanentDeleteConfirm)
    }

    private fun exercise(
        uuid: String,
        position: Int = 0,
    ): TrainingExerciseItem = TrainingExerciseItem(
        exerciseUuid = uuid,
        exerciseName = "Bench Press",
        exerciseType = ExerciseTypeUiModel.WEIGHTED,
        tags = persistentListOf(),
        position = position,
        planSets = null,
        planSummary = "",
    )
}
