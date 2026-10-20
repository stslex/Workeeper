// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagItem
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.domain.model.ActiveSessionDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.single_training.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.ExercisePlanDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.PickerExercise
import io.github.stslex.workeeper.feature.single_training.domain.model.SessionDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.StartSessionConflict
import io.github.stslex.workeeper.feature.single_training.domain.model.TagDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingChangeDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingExerciseDetail
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingEditCopy
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.store.DialogState
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.resources.Res
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_conflict_progress_format
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_permanent_delete_body
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_permanent_delete_confirm
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_permanent_delete_impact
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_permanent_delete_title
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ClickHandlerTest {

    private val interactor = FakeSingleTrainingInteractor()
    private val initialState = State.create(uuid = null)
    private val stateFlow = MutableStateFlow(initialState)

    private val store = FakeClickHandlerStore(stateFlow)

    private val resolver = StrictClickStringResolver()

    private val handler = ClickHandler(interactor, resolver, Dispatchers.Unconfined, store)

    /** What `Init` resolves (D2); a distinct string per field, so a read of the wrong one fails. */
    private val editCopy = TrainingEditCopy(
        noExercisesError = "no-exercises",
        exerciseRemoved = "exercise-removed",
        setRemoved = "set-removed",
    )

    /** Meaningful only while Save is never disabled (§26): a predicate would hide both. */
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
        val captured = store.events.last()
        assertTrue(captured is Event.ShowSaveError)
    }

    /** The other direction: both branches are gates, not unconditional flags. */
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
        assertEquals(0, store.events.count { it is Event.ShowSaveError })
    }

    @Test
    fun `OnEditClick flips into Edit mode`() {
        stateFlow.value = stateFlow.value.copy(mode = State.Mode.Read)
        handler.invoke(Action.Click.OnEditClick)
        assertTrue(stateFlow.value.mode is State.Mode.Edit)
    }

    /** D-OPEN-8: a multi-insert opens the first card only. */
    @Test
    fun `OnPickerConfirm opens the first inserted card only`() = runTest {
        store.launches = Launches.RunThenSuccess(this)
        interactor.resolvedExercises[listOf("ex-1", "ex-2")] = listOf(
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
        advanceUntilIdle()

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

    /** ED14: expansion is per card, never an accordion. */
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

    /** Removing the last set normalizes the plan to null — `plan_sets IS NULL` is the shape. */
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
        val captured = store.events.last()
        assertTrue(captured is Event.HapticClick)
        assertEquals(HapticFeedbackType.ContextClick, (captured as Event.HapticClick).type)
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
    fun `OnStartSessionClick reports the persisted active session progress`() = runTest {
        store.launches = Launches.RunActionOnly(this)
        stateFlow.value = stateFlow.value.copy(uuid = "training-1", name = "Push Day")
        interactor.conflicts["training-1"] =
            StartSessionConflict.NeedsUserChoice(
                active = ActiveSessionDomain(
                    sessionUuid = "active-1",
                    trainingUuid = "active-training",
                    startedAt = 1L,
                ),
                doneCount = 1,
                totalCount = 2,
            )

        handler.invoke(Action.Click.OnStartSessionClick)
        advanceUntilIdle()

        val dialog = stateFlow.value.dialogState as DialogState.ActiveSessionConflict
        assertEquals("1 of 2 exercises done", dialog.progressLabel)
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
        assertTrue(
            Action.Navigation.OpenLiveWorkout(
                sessionUuid = "session-1",
                trainingUuid = null,
            ) in store.consumed,
        )
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
        assertEquals(0, store.consumed.count { it is Action.Navigation.Back })
    }

    /** ED14 governs entering: the leak only shows on the expand → save → Edit path. */
    @Test
    fun `entering the editor after a save collapses every card`() = runTest {
        store.launches = Launches.RunThenSuccess(this)
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
        advanceUntilIdle()
        assertEquals(State.Mode.Read, stateFlow.value.mode)

        handler.invoke(Action.Click.OnEditClick)

        assertTrue(stateFlow.value.expandedExerciseUuids.isEmpty())
    }

    /** The dirty signature is narrower than the snapshot: a refreshed name is not an edit. */
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

    /** Three things the snapshot must put back; one test each, because they fail separately. */
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

    /** §4 row 2: `✕` is a draft edit; its undo restores the item where it stood. */
    @Test
    fun `remove emits the undo toast and the undo restores item position and expansion`() {
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            exercises = persistentListOf(exercise("ex-1"), exercise("ex-2", position = 1)),
            expandedExerciseUuids = persistentSetOf("ex-1"),
        )
        val events = store.events

        handler.invoke(Action.Click.OnExerciseRemove("ex-1"))

        assertEquals(listOf("ex-2"), stateFlow.value.exercises.map { it.exerciseUuid })
        assertEquals(0, stateFlow.value.exercises.single().position)
        val undo = events.filterIsInstance<Event.ShowExerciseRemovedUndo>().single()
        assertEquals("ex-1", undo.item.exerciseUuid)
        assertTrue(undo.wasExpanded)

        handler.invoke(
            Action.Click.OnUndoExerciseRemove(
                item = undo.item,
                wasExpanded = undo.wasExpanded,
                draftEpoch = undo.draftEpoch,
            ),
        )

        assertEquals(listOf("ex-1", "ex-2"), stateFlow.value.exercises.map { it.exerciseUuid })
        assertEquals(listOf(0, 1), stateFlow.value.exercises.map { it.position })
        assertTrue("ex-1" in stateFlow.value.expandedExerciseUuids)
    }

    @Test
    fun `minus set in a card emits the undo toast and the undo restores the row`() {
        val set = PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK)
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            exercises = persistentListOf(
                exercise("ex-1").copy(planSets = persistentListOf(set), planSummary = "60×10"),
            ),
        )
        val events = store.events

        handler.invoke(
            Action.Click.OnExercisePlanAction("ex-1", PlanEditorBodyAction.OnSetRemove(0)),
        )
        assertEquals(null, stateFlow.value.exercises.single().planSets)
        val undo = events.filterIsInstance<Event.ShowSetRemovedUndo>().single()
        assertEquals(set, undo.set)

        handler.invoke(
            Action.Click.OnUndoSetRemove(
                exerciseUuid = undo.exerciseUuid,
                set = undo.set,
                index = undo.index,
                draftEpoch = undo.draftEpoch,
            ),
        )
        assertEquals<List<PlanSetUiModel>?>(listOf(set), stateFlow.value.exercises.single().planSets)
        assertEquals("60×10", stateFlow.value.exercises.single().planSummary)
    }

    /** The toast outlives the draft: after Save's flip to Read the guard makes it inert. */
    @Test
    fun `a stale set undo after save flipped to read edits nothing`() {
        val set = PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK)
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            exercises = persistentListOf(
                exercise("ex-1").copy(planSets = persistentListOf(set), planSummary = "60×10"),
            ),
        )
        val events = store.events
        handler.invoke(
            Action.Click.OnExercisePlanAction("ex-1", PlanEditorBodyAction.OnSetRemove(0)),
        )
        val undo = events.filterIsInstance<Event.ShowSetRemovedUndo>().single()

        // What processSaveClick's success does on this screen: the draft ends, Read begins.
        stateFlow.value = stateFlow.value.copy(mode = State.Mode.Read)

        handler.invoke(
            Action.Click.OnUndoSetRemove(
                exerciseUuid = undo.exerciseUuid,
                set = undo.set,
                index = undo.index,
                draftEpoch = undo.draftEpoch,
            ),
        )

        assertEquals(null, stateFlow.value.exercises.single().planSets)
    }

    /**
     * The guard is a disjunction, so each clause gets the case only it blocks: this is the set
     * undo's epoch clause, the pair below is the exercise undo's mode clause.
     */
    @Test
    fun `a stale set undo does not edit a re-entered draft`() {
        val set = PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK)
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            exercises = persistentListOf(
                exercise("ex-1").copy(planSets = persistentListOf(set), planSummary = "60×10"),
            ),
        )
        val events = store.events
        handler.invoke(
            Action.Click.OnExercisePlanAction("ex-1", PlanEditorBodyAction.OnSetRemove(0)),
        )
        val undo = events.filterIsInstance<Event.ShowSetRemovedUndo>().single()

        stateFlow.value = stateFlow.value.copy(mode = State.Mode.Read) // Save's flip.
        handler.invoke(Action.Click.OnEditClick) // Mode is Edit again — only the epoch differs.
        assertEquals(undo.draftEpoch + 1, stateFlow.value.draftEpoch)

        handler.invoke(
            Action.Click.OnUndoSetRemove(
                exerciseUuid = undo.exerciseUuid,
                set = undo.set,
                index = undo.index,
                draftEpoch = undo.draftEpoch,
            ),
        )

        assertEquals(null, stateFlow.value.exercises.single().planSets)
    }

    /** The exercise undo's mode-clause case — the epoch still matches after Save's flip. */
    @Test
    fun `a stale exercise undo after save flipped to read edits nothing`() {
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            exercises = persistentListOf(exercise("ex-1"), exercise("ex-2", position = 1)),
        )
        val events = store.events
        handler.invoke(Action.Click.OnExerciseRemove("ex-1"))
        val undo = events.filterIsInstance<Event.ShowExerciseRemovedUndo>().single()

        stateFlow.value = stateFlow.value.copy(mode = State.Mode.Read) // Save's flip.

        handler.invoke(
            Action.Click.OnUndoExerciseRemove(
                item = undo.item,
                wasExpanded = undo.wasExpanded,
                draftEpoch = undo.draftEpoch,
            ),
        )

        assertEquals(listOf("ex-2"), stateFlow.value.exercises.map { it.exerciseUuid })
    }

    /** Save → Edit again inside the toast's window: the epoch bump makes the stale undo miss. */
    @Test
    fun `a stale exercise undo does not edit a re-entered draft`() {
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            exercises = persistentListOf(exercise("ex-1"), exercise("ex-2", position = 1)),
        )
        val events = store.events
        handler.invoke(Action.Click.OnExerciseRemove("ex-1"))
        val undo = events.filterIsInstance<Event.ShowExerciseRemovedUndo>().single()

        stateFlow.value = stateFlow.value.copy(mode = State.Mode.Read) // Save's flip.
        handler.invoke(Action.Click.OnEditClick)
        assertEquals(undo.draftEpoch + 1, stateFlow.value.draftEpoch)

        handler.invoke(
            Action.Click.OnUndoExerciseRemove(
                item = undo.item,
                wasExpanded = undo.wasExpanded,
                draftEpoch = undo.draftEpoch,
            ),
        )

        assertEquals(listOf("ex-2"), stateFlow.value.exercises.map { it.exerciseUuid })
    }

    /**
     * The in-flight interval: mode and epoch both still match, so [State.isSaving] is the only
     * clause left. The inert `launch` mock is the simulation — dispatched, never completed.
     */
    @Test
    fun `an undo during the save's write edits nothing`() {
        val set = PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK)
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            name = "Push Day",
            exercises = persistentListOf(
                exercise("ex-1").copy(planSets = persistentListOf(set), planSummary = "60×10"),
            ),
        )
        val events = store.events
        handler.invoke(
            Action.Click.OnExercisePlanAction("ex-1", PlanEditorBodyAction.OnSetRemove(0)),
        )
        val undo = events.filterIsInstance<Event.ShowSetRemovedUndo>().single()

        handler.invoke(Action.Click.OnSaveClick)
        assertTrue(stateFlow.value.isSaving)

        handler.invoke(
            Action.Click.OnUndoSetRemove(
                exerciseUuid = undo.exerciseUuid,
                set = undo.set,
                index = undo.index,
                draftEpoch = undo.draftEpoch,
            ),
        )

        assertEquals(null, stateFlow.value.exercises.single().planSets)
    }

    /** A failed write keeps the draft alive, and the draft's undos re-arm with it. */
    @Test
    fun `a failed save re-arms the draft's undos`() = runTest {
        val set = PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK)
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            name = "Push Day",
            exercises = persistentListOf(
                exercise("ex-1").copy(planSets = persistentListOf(set), planSummary = "60×10"),
            ),
        )
        val events = store.events
        handler.invoke(
            Action.Click.OnExercisePlanAction("ex-1", PlanEditorBodyAction.OnSetRemove(0)),
        )
        val undo = events.filterIsInstance<Event.ShowSetRemovedUndo>().single()

        store.launches = Launches.FailWith(this, RuntimeException("db full"))
        handler.invoke(Action.Click.OnSaveClick)
        advanceUntilIdle()
        assertFalse(stateFlow.value.isSaving)

        handler.invoke(
            Action.Click.OnUndoSetRemove(
                exerciseUuid = undo.exerciseUuid,
                set = undo.set,
                index = undo.index,
                draftEpoch = undo.draftEpoch,
            ),
        )

        assertEquals<List<PlanSetUiModel>?>(listOf(set), stateFlow.value.exercises.single().planSets)
    }

    /** The same interval, other direction: a dispatched write blocks the discard sheet. */
    @Test
    fun `a cancel during the save's write raises nothing and reverts nothing`() {
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            name = "Push Day v2",
            exercises = persistentListOf(exercise("ex-1")),
            originalSnapshot = State.Snapshot(
                name = "Push Day",
                description = "",
                tagUuids = emptyList(),
                exercises = persistentListOf(exercise("ex-1")),
            ),
        )
        handler.invoke(Action.Click.OnSaveClick)
        assertTrue(stateFlow.value.isSaving)

        handler.invoke(Action.Click.OnCancelClick)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        assertTrue(stateFlow.value.mode is State.Mode.Edit)
        assertEquals("Push Day v2", stateFlow.value.name)
    }

    /** The confirm is a second action after the sheet was raised, so it carries its own guard. */
    @Test
    fun `a confirmed discard during the save's write reverts nothing`() {
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            isSaving = true,
            name = "Push Day v2",
            originalSnapshot = State.Snapshot(
                name = "Push Day",
                description = "",
                tagUuids = emptyList(),
                exercises = persistentListOf(exercise("ex-1")),
            ),
            dialogState = DialogState.DiscardConfirm,
        )

        handler.invoke(Action.Click.OnConfirmDiscard)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        assertTrue(stateFlow.value.mode is State.Mode.Edit)
        assertEquals("Push Day v2", stateFlow.value.name)
    }

    /** A flag orphaned with a dead draft must not gag the next draft's undos. */
    @Test
    fun `Edit entry resets a stuck isSaving`() {
        stateFlow.value = stateFlow.value.copy(mode = State.Mode.Read, isSaving = true)
        handler.invoke(Action.Click.OnEditClick)
        assertFalse(stateFlow.value.isSaving)
    }

    /** The repository returns the EXISTING row for a known name, so a create cannot chip twice. */
    @Test
    fun `createTag resolving to an already-selected tag does not duplicate the chip`() = runTest {
        stateFlow.value = stateFlow.value.copy(
            tags = persistentListOf(AppTagItem(uuid = "t1", name = "Push")),
            tagSearchQuery = " Push ",
        )
        store.launches = Launches.RunThenSuccess(this)
        interactor.createdTags["Push"] = TagDomain(uuid = "t1", name = "Push")

        handler.invoke(Action.Click.OnTagCreate(" Push "))
        advanceUntilIdle()

        assertEquals(listOf("t1"), stateFlow.value.tags.map { it.uuid })
        assertEquals("", stateFlow.value.tagSearchQuery)
    }

    /** Both removals queue toasts: a set undo landing while its card is absent stashes. */
    @Test
    fun `a set undo whose card was already removed waits for the card`() {
        val set = PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK)
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            exercises = persistentListOf(
                exercise("ex-1").copy(planSets = persistentListOf(set), planSummary = "60×10"),
                exercise("ex-2", position = 1),
            ),
        )
        val events = store.events
        handler.invoke(
            Action.Click.OnExercisePlanAction("ex-1", PlanEditorBodyAction.OnSetRemove(0)),
        )
        val setUndo = events.filterIsInstance<Event.ShowSetRemovedUndo>().single()
        handler.invoke(Action.Click.OnExerciseRemove("ex-1"))
        val exerciseUndo = events.filterIsInstance<Event.ShowExerciseRemovedUndo>().single()

        // The set toast shows first (FIFO); its undo lands while the card is absent.
        handler.invoke(
            Action.Click.OnUndoSetRemove(
                exerciseUuid = setUndo.exerciseUuid,
                set = setUndo.set,
                index = setUndo.index,
                draftEpoch = setUndo.draftEpoch,
            ),
        )
        assertEquals(listOf("ex-2"), stateFlow.value.exercises.map { it.exerciseUuid })

        handler.invoke(
            Action.Click.OnUndoExerciseRemove(
                item = exerciseUndo.item,
                wasExpanded = exerciseUndo.wasExpanded,
                draftEpoch = exerciseUndo.draftEpoch,
            ),
        )

        assertEquals(listOf("ex-1", "ex-2"), stateFlow.value.exercises.map { it.exerciseUuid })
        assertEquals<List<PlanSetUiModel>?>(listOf(set), stateFlow.value.exercises.first().planSets)
        assertEquals("60×10", stateFlow.value.exercises.first().planSummary)
        assertTrue(stateFlow.value.pendingSetRestores.isEmpty())
    }

    /** A stash belongs to its draft: entering Edit again starts clean. */
    @Test
    fun `stashed set restores die with the draft`() {
        val set = PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK)
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            exercises = persistentListOf(
                exercise("ex-1").copy(planSets = persistentListOf(set), planSummary = "60×10"),
            ),
        )
        val events = store.events
        handler.invoke(
            Action.Click.OnExercisePlanAction("ex-1", PlanEditorBodyAction.OnSetRemove(0)),
        )
        val setUndo = events.filterIsInstance<Event.ShowSetRemovedUndo>().single()
        handler.invoke(Action.Click.OnExerciseRemove("ex-1"))
        handler.invoke(
            Action.Click.OnUndoSetRemove(
                exerciseUuid = setUndo.exerciseUuid,
                set = setUndo.set,
                index = setUndo.index,
                draftEpoch = setUndo.draftEpoch,
            ),
        )
        assertEquals(1, stateFlow.value.pendingSetRestores.size)

        stateFlow.value = stateFlow.value.copy(mode = State.Mode.Read) // Save's flip.
        handler.invoke(Action.Click.OnEditClick)

        assertTrue(stateFlow.value.pendingSetRestores.isEmpty())
    }

    /** The card can return by the picker: the fresh card discards the dead chain's stash. */
    @Test
    fun `a picker re-add discards the stash for the returned card`() = runTest {
        val set = PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK)
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            exercises = persistentListOf(
                exercise("ex-1").copy(planSets = persistentListOf(set), planSummary = "60×10"),
            ),
        )
        val events = store.events
        handler.invoke(
            Action.Click.OnExercisePlanAction("ex-1", PlanEditorBodyAction.OnSetRemove(0)),
        )
        val setUndo = events.filterIsInstance<Event.ShowSetRemovedUndo>().single()
        handler.invoke(Action.Click.OnExerciseRemove("ex-1"))
        handler.invoke(
            Action.Click.OnUndoSetRemove(
                exerciseUuid = setUndo.exerciseUuid,
                set = setUndo.set,
                index = setUndo.index,
                draftEpoch = setUndo.draftEpoch,
            ),
        )
        assertEquals(1, stateFlow.value.pendingSetRestores.size)

        // The exercise toast expires unheeded; the user re-adds the card by the picker.
        store.launches = Launches.RunThenSuccess(this)
        interactor.resolvedExercises[listOf("ex-1")] = listOf(
            PickerExercise(
                exercise = ExerciseDomain("ex-1", "Bench", ExerciseTypeDomain.WEIGHTED, null, null),
                labels = emptyList(),
            ),
        )
        stateFlow.value = stateFlow.value.copy(
            pickerState = State.PickerState.Open(
                query = "",
                results = persistentListOf(),
                selectedUuids = persistentListOf("ex-1"),
            ),
        )
        handler.invoke(Action.Click.OnPickerConfirm)
        advanceUntilIdle()
        assertTrue(stateFlow.value.pendingSetRestores.isEmpty())

        // The NEW card's own remove-and-undo must not resurrect the old set.
        handler.invoke(Action.Click.OnExerciseRemove("ex-1"))
        val exerciseUndo = events.filterIsInstance<Event.ShowExerciseRemovedUndo>().last()
        handler.invoke(
            Action.Click.OnUndoExerciseRemove(
                item = exerciseUndo.item,
                wasExpanded = exerciseUndo.wasExpanded,
                draftEpoch = exerciseUndo.draftEpoch,
            ),
        )
        assertEquals(null, stateFlow.value.exercises.single().planSets)
    }

    /** The same discard on the undo's own already-back branch — the picker beat it there. */
    @Test
    fun `an exercise undo that finds its card already back discards the stash`() {
        val set = PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK)
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            exercises = persistentListOf(
                exercise("ex-1").copy(planSets = persistentListOf(set), planSummary = "60×10"),
            ),
        )
        val events = store.events
        handler.invoke(
            Action.Click.OnExercisePlanAction("ex-1", PlanEditorBodyAction.OnSetRemove(0)),
        )
        val setUndo = events.filterIsInstance<Event.ShowSetRemovedUndo>().single()
        handler.invoke(Action.Click.OnExerciseRemove("ex-1"))
        handler.invoke(
            Action.Click.OnUndoSetRemove(
                exerciseUuid = setUndo.exerciseUuid,
                set = setUndo.set,
                index = setUndo.index,
                draftEpoch = setUndo.draftEpoch,
            ),
        )
        val exerciseUndo = events.filterIsInstance<Event.ShowExerciseRemovedUndo>().single()

        // The picker already returned the card (state set directly, the insert's shape).
        stateFlow.value = stateFlow.value.copy(
            exercises = persistentListOf(exercise("ex-1")),
        )
        handler.invoke(
            Action.Click.OnUndoExerciseRemove(
                item = exerciseUndo.item,
                wasExpanded = exerciseUndo.wasExpanded,
                draftEpoch = exerciseUndo.draftEpoch,
            ),
        )

        assertTrue(stateFlow.value.pendingSetRestores.isEmpty())
        assertEquals(null, stateFlow.value.exercises.single().planSets)
    }

    /** The resolution is async: the completion must dedup against the state it lands on. */
    @Test
    fun `a late picker resolution does not duplicate a card the undo restored`() = runTest {
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            exercises = persistentListOf(exercise("ex-1")),
        )
        val events = store.events
        handler.invoke(Action.Click.OnExerciseRemove("ex-1"))
        val undo = events.filterIsInstance<Event.ShowExerciseRemovedUndo>().single()

        // The picker's query dispatches but does NOT complete; the store captures it.
        store.launches = Launches.Captured
        interactor.resolvedExercises[listOf("ex-1")] = listOf(
            PickerExercise(
                exercise = ExerciseDomain("ex-1", "Bench", ExerciseTypeDomain.WEIGHTED, null, null),
                labels = emptyList(),
            ),
        )
        stateFlow.value = stateFlow.value.copy(
            pickerState = State.PickerState.Open(
                query = "",
                results = persistentListOf(),
                selectedUuids = persistentListOf("ex-1"),
            ),
        )
        handler.invoke(Action.Click.OnPickerConfirm)

        handler.invoke(
            Action.Click.OnUndoExerciseRemove(
                item = undo.item,
                wasExpanded = undo.wasExpanded,
                draftEpoch = undo.draftEpoch,
            ),
        )
        assertEquals(listOf("ex-1"), stateFlow.value.exercises.map { it.exerciseUuid })

        // The in-flight resolution lands AFTER the restore.
        store.completeCaptured()

        assertEquals(listOf("ex-1"), stateFlow.value.exercises.map { it.exerciseUuid })
        assertEquals(listOf(0), stateFlow.value.exercises.map { it.position })
    }

    @Test
    fun `the dashed add chip opens the tag picker sheet`() {
        handler.invoke(Action.Click.OnTagAddClick)
        assertEquals(DialogState.TagPicker, stateFlow.value.dialogState)
    }

    @Test
    fun `dismissing the tag picker clears the query with the sheet`() {
        stateFlow.value = stateFlow.value.copy(
            dialogState = DialogState.TagPicker,
            tagSearchQuery = "кар",
        )
        handler.invoke(Action.Click.OnTagPickerDismiss)
        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        assertEquals("", stateFlow.value.tagSearchQuery)
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

    /** One sealed field: the confirm's write replaces the menu variant in the same transition. */
    @Test
    fun `permanent delete replaces the menu with its confirm in one write`() = runTest {
        stateFlow.value = stateFlow.value.copy(
            uuid = "training-1",
            canPermanentlyDelete = true,
            dialogState = DialogState.DetailMenu,
        )
        // D2: the confirm's copy resolves in a launch, so the launch runs here.
        store.launches = Launches.RunThenSuccess(this)

        handler.invoke(Action.Click.OnPermanentDeleteClick)
        advanceUntilIdle()

        assertTrue(stateFlow.value.dialogState is DialogState.PermanentDeleteConfirm)
    }

    /** D2: the confirm lands only on the menu it replaces; a dismissal in its window wins. */
    @Test
    fun `a menu dismissed before the confirm's copy lands stays dismissed`() = runTest {
        stateFlow.value = stateFlow.value.copy(
            uuid = "training-1",
            canPermanentlyDelete = true,
            dialogState = DialogState.DetailMenu,
        )
        // The copy's launch dispatches but does NOT complete; the store captures it.
        store.launches = Launches.Captured

        handler.invoke(Action.Click.OnPermanentDeleteClick)
        assertEquals(DialogState.DetailMenu, stateFlow.value.dialogState)

        handler.invoke(Action.Click.OnDetailMenuDismiss)
        store.completeCaptured()

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    /** D2: the save error shows the copy `Init` put in State, with no read on the click. */
    @Test
    fun `the save error carries the copy Init resolved`() {
        stateFlow.value = stateFlow.value.copy(name = "Push Day", editCopy = editCopy)
        val events = store.events

        handler.invoke(Action.Click.OnSaveClick)

        assertEquals("no-exercises", events.filterIsInstance<Event.ShowSaveError>().single().message)
    }

    /** D2: each undo toast carries its own field of the copy `Init` put in State. */
    @Test
    fun `the undo toasts carry the copy Init resolved`() {
        val set = PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK)
        stateFlow.value = stateFlow.value.copy(
            mode = State.Mode.Edit(isCreate = false),
            exercises = persistentListOf(
                exercise("ex-1").copy(planSets = persistentListOf(set), planSummary = "60×10"),
                exercise("ex-2", position = 1),
            ),
            editCopy = editCopy,
        )
        val events = store.events

        handler.invoke(
            Action.Click.OnExercisePlanAction("ex-1", PlanEditorBodyAction.OnSetRemove(0)),
        )
        handler.invoke(Action.Click.OnExerciseRemove("ex-2"))

        assertEquals("set-removed", events.filterIsInstance<Event.ShowSetRemovedUndo>().single().message)
        assertEquals(
            "exercise-removed",
            events.filterIsInstance<Event.ShowExerciseRemovedUndo>().single().message,
        )
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

    /** How [FakeClickHandlerStore.launch] answers for one identity (SPEC 11.1's table). */
    private sealed interface Launches {

        /** The relaxed mock's default: the launch is recorded and nothing runs. */
        data object Recorded : Launches

        /** The action, then `onSuccess`, launched on the identity's `runTest` scheduler. */
        class RunThenSuccess(val scope: CoroutineScope) : Launches

        /** The action only, launched on the identity's `runTest` scheduler. */
        class RunActionOnly(val scope: CoroutineScope) : Launches

        /** `onError` with [error] only, launched on the identity's `runTest` scheduler. */
        class FailWith(val scope: CoroutineScope, val error: Throwable) : Launches

        /** Held until the identity completes it through [FakeClickHandlerStore.completeCaptured]. */
        data object Captured : Launches
    }

    /**
     * The store as the relaxed MockK store answered it: [state] is the identity's flow,
     * [updateState] and the update-lambda [updateStateImmediate] apply to it, [sendEvent] and
     * [consume] record, and [launch] follows [launches] and returns a [Job]. A throw from a launch
     * that runs is routed nowhere, so it fails the identity. Every other member fails fast.
     */
    private class FakeClickHandlerStore(
        private val flow: MutableStateFlow<State>,
    ) : SingleTrainingHandlerStore {

        val events = mutableListOf<Event>()
        val consumed = mutableListOf<Action>()
        var launches: Launches = Launches.Recorded
        private var captured: (suspend () -> Unit)? = null

        override val state: StateFlow<State> = flow

        override val lastAction: Nothing
            get() = error("lastAction must not be read by ClickHandler")

        override val logger: Logger
            get() = error("logger must not be read by ClickHandler")

        override fun sendEvent(event: Event) {
            events.add(event)
        }

        override fun consume(action: Action) {
            consumed.add(action)
        }

        override suspend fun consumeOnMain(action: Action): Nothing =
            error("consumeOnMain must not be reached by these identities")

        override fun updateState(update: (State) -> State) {
            flow.value = update(flow.value)
        }

        override suspend fun updateStateImmediate(update: suspend (State) -> State) {
            flow.value = update(flow.value)
        }

        override suspend fun updateStateImmediate(state: State): Nothing =
            error("updateStateImmediate(state) must not be used by ClickHandler")

        override fun <T> launch(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            action: suspend CoroutineScope.() -> T,
        ): Job {
            when (val mode = launches) {
                Launches.Recorded -> Unit
                is Launches.RunThenSuccess -> mode.scope.launch { onSuccess(action()) }
                is Launches.RunActionOnly -> mode.scope.launch { action() }
                is Launches.FailWith -> mode.scope.launch { onError(mode.error) }
                Launches.Captured -> captured = { coroutineScope { onSuccess(action()) } }
            }
            return Job()
        }

        /** Runs the captured launch to its end — the action, then `onSuccess` — on the caller. */
        suspend fun completeCaptured() {
            val pending = checkNotNull(captured) { "no launch was captured" }
            captured = null
            pending()
        }

        override fun <T> launchDefault(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            action: suspend CoroutineScope.() -> T,
        ): Nothing = error("launchDefault must not be used by ClickHandler")

        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Nothing = error("Flow.launch must not be used by ClickHandler")
    }

    /**
     * The interactor as the relaxed MockK one answered it: what an identity sets up answers from
     * [resolvedExercises], [conflicts] and [createdTags], and every other member answers the relaxed
     * default — `Unit`, an empty list or flow, `false`, `0` or `null`. A member whose relaxed answer
     * was a mock throws instead; no identity reaches one unset. Every call is counted in [calls].
     */
    private class FakeSingleTrainingInteractor : SingleTrainingInteractor {

        val resolvedExercises = mutableMapOf<List<String>, List<PickerExercise>>()
        val conflicts = mutableMapOf<String, StartSessionConflict>()
        val createdTags = mutableMapOf<String, TagDomain>()
        val calls = mutableListOf<String>()

        override suspend fun getTraining(uuid: String): TrainingDomain? = called("getTraining", null)

        override suspend fun getTrainingExercises(trainingUuid: String): List<TrainingExerciseDetail> =
            called("getTrainingExercises", emptyList())

        override suspend fun getRecentSessions(trainingUuid: String, limit: Int): List<SessionDomain> =
            called("getRecentSessions", emptyList())

        override suspend fun countSessions(trainingUuid: String): Int = called("countSessions", 0)

        override fun observeAvailableTags(): Flow<List<TagDomain>> = called("observeAvailableTags", emptyFlow())

        override suspend fun saveTraining(snapshot: TrainingChangeDomain, plans: List<ExercisePlanDomain>) {
            called("saveTraining", Unit)
        }

        override suspend fun createTag(name: String): TagDomain =
            called("createTag", createdTags[name] ?: error("createTag($name) was not set up"))

        override suspend fun archive(uuid: String): ArchiveResult {
            calls.add("archive")
            error("archive was not set up")
        }

        override suspend fun permanentlyDelete(uuid: String) {
            called("permanentlyDelete", Unit)
        }

        override suspend fun canPermanentlyDelete(uuid: String): Boolean = called("canPermanentlyDelete", false)

        override fun observeAnyActiveSession(): Flow<ActiveSessionDomain?> =
            called("observeAnyActiveSession", emptyFlow())

        override suspend fun searchExercisesForPicker(
            query: String,
            excludeUuids: Set<String>,
        ): List<PickerExercise> = called("searchExercisesForPicker", emptyList())

        override suspend fun resolveExercises(uuids: List<String>): List<PickerExercise> =
            called("resolveExercises", resolvedExercises[uuids] ?: emptyList())

        override suspend fun resolveStartSessionConflict(requestedTrainingUuid: String): StartSessionConflict =
            called(
                "resolveStartSessionConflict",
                conflicts[requestedTrainingUuid]
                    ?: error("resolveStartSessionConflict($requestedTrainingUuid) was not set up"),
            )

        override suspend fun deleteSession(sessionUuid: String) {
            called("deleteSession", Unit)
        }

        override suspend fun getLabels(exerciseUuid: String): List<String> = called("getLabels", emptyList())

        private fun <T> called(member: String, answer: T): T {
            calls.add(member)
            return answer
        }
    }

    /**
     * Strict: answers exactly the five reads these identities reach, and only with the arguments
     * production passes — the conflict progress with its two counts, the confirm's title with the
     * training's name (State.create's empty default in both identities that reach it), and the
     * confirm's other three verbatim. Any other resource or argument list throws.
     */
    private class StrictClickStringResolver : StringResolver {

        override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String {
            val args = formatArgs.toList()
            return when (resource) {
                Res.string.feature_training_detail_conflict_progress_format -> {
                    check(args == listOf<Any>(1, 2)) { "the progress passes done, then total; got $args" }
                    "1 of 2 exercises done"
                }

                Res.string.feature_training_detail_permanent_delete_title -> {
                    check(args == listOf<Any>("")) { "the title passes the training's name; got $args" }
                    ""
                }

                Res.string.feature_training_detail_permanent_delete_body,
                Res.string.feature_training_detail_permanent_delete_impact,
                Res.string.feature_training_detail_permanent_delete_confirm,
                -> {
                    check(args.isEmpty()) { "$resource is read verbatim; got $args" }
                    ""
                }

                else -> error("these identities must not read $resource")
            }
        }

        override suspend fun getPluralString(
            resource: PluralStringResource,
            quantity: Int,
            vararg formatArgs: Any,
        ): String = error("ClickHandler reads no plural; got $resource")
    }
}
