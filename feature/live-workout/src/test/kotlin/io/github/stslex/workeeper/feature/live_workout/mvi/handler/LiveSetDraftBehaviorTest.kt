// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveSetMutator
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.StateStatusMapper
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Locks the draft seed/update invariant: every draft mutation must keep all unrelated
 * fields from the visible row (performed > draft > plan > fallback). Type changes must
 * not reset weight/reps; weight/reps changes must not reset type.
 *
 * Note on `Action.Click.OnSetTypeSelect.type` semantics: the action carries the row's
 * **current** type (what the chip displays at click time); the handler advances it
 * with `SetTypeUiModel.next()`. So sending `type = WORK` produces `FAILURE`, sending
 * `type = DROP` produces `WARMUP`, etc. These tests use that production semantics
 * verbatim and assert on the resulting field combination, not on the cycling rule
 * itself.
 */
internal class LiveSetDraftBehaviorTest {

    private val interactor = mockk<LiveWorkoutInteractor>(relaxed = true)
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    private val pickerHandler = mockk<ExercisePickerHandler>(relaxed = true)
    private val statusMapper = StateStatusMapper(resourceWrapper)
    private val setMutator = LiveSetMutator(resourceWrapper, statusMapper)

    @Test
    fun `OnSetTypeSelect with no draft seeds from plan and preserves weight and reps`() {
        // Plan: weight=100.0, reps=5, type=WORK. The chip currently shows WORK so the
        // action carries WORK; handler advances to FAILURE. Weight + reps must be kept.
        val stateFlow = stateWithPlan(SetTypeUiModel.WORK)
        val handler = clickHandler(stateFlow)

        handler.invoke(Action.Click.OnSetTypeSelect(PE_UUID, position = 0, type = SetTypeUiModel.WORK))

        val draft = stateFlow.value.setDrafts[State.DraftKey(PE_UUID, 0)]
        assertNotNull(draft)
        assertEquals(100.0, draft?.weight)
        assertEquals(5, draft?.reps)
        assertEquals(SetTypeUiModel.FAILURE, draft?.type)
        assertEquals(false, draft?.isDone)
    }

    @Test
    fun `OnSetWeightChange with no draft seeds from plan and preserves type and reps`() {
        // Plan: weight=100.0, reps=5, type=WARMUP. Weight change to 120 must keep
        // reps and type.
        val stateFlow = stateWithPlan(SetTypeUiModel.WARMUP)
        val handler = inputHandler(stateFlow)

        handler.invoke(Action.Input.OnSetWeightChange(PE_UUID, position = 0, value = 120.0))

        val draft = stateFlow.value.setDrafts[State.DraftKey(PE_UUID, 0)]
        assertNotNull(draft)
        assertEquals(120.0, draft?.weight)
        assertEquals(5, draft?.reps)
        assertEquals(SetTypeUiModel.WARMUP, draft?.type)
        assertEquals(false, draft?.isDone)
    }

    @Test
    fun `OnSetRepsChange after OnSetTypeSelect preserves the type set by the previous draft`() {
        val stateFlow = stateWithPlan(SetTypeUiModel.WORK)
        val click = clickHandler(stateFlow)
        val input = inputHandler(stateFlow)

        // Type chip click: WORK -> FAILURE.
        click.invoke(Action.Click.OnSetTypeSelect(PE_UUID, position = 0, type = SetTypeUiModel.WORK))
        // Then change reps. Must not lose the FAILURE we just set.
        input.invoke(Action.Input.OnSetRepsChange(PE_UUID, position = 0, value = 8))

        val draft = stateFlow.value.setDrafts[State.DraftKey(PE_UUID, 0)]
        assertNotNull(draft)
        assertEquals(100.0, draft?.weight)
        assertEquals(8, draft?.reps)
        assertEquals(SetTypeUiModel.FAILURE, draft?.type)
        assertEquals(false, draft?.isDone)
    }

    @Test
    fun `OnSetTypeSelect after OnSetWeightChange preserves the edited weight`() {
        val stateFlow = stateWithPlan(SetTypeUiModel.WORK)
        val click = clickHandler(stateFlow)
        val input = inputHandler(stateFlow)

        input.invoke(Action.Input.OnSetWeightChange(PE_UUID, position = 0, value = 120.0))
        // Existing draft has type=WORK, so action carries WORK; handler advances to FAILURE.
        click.invoke(Action.Click.OnSetTypeSelect(PE_UUID, position = 0, type = SetTypeUiModel.WORK))

        val draft = stateFlow.value.setDrafts[State.DraftKey(PE_UUID, 0)]
        assertNotNull(draft)
        assertEquals(120.0, draft?.weight)
        assertEquals(5, draft?.reps)
        assertEquals(SetTypeUiModel.FAILURE, draft?.type)
        assertEquals(false, draft?.isDone)
    }

    @Test
    fun `OnSetRepsChange after OnSetWeightChange preserves the edited weight and plan type`() {
        val stateFlow = stateWithPlan(SetTypeUiModel.WORK)
        val input = inputHandler(stateFlow)

        input.invoke(Action.Input.OnSetWeightChange(PE_UUID, position = 0, value = 120.0))
        input.invoke(Action.Input.OnSetRepsChange(PE_UUID, position = 0, value = 8))

        val draft = stateFlow.value.setDrafts[State.DraftKey(PE_UUID, 0)]
        assertNotNull(draft)
        assertEquals(120.0, draft?.weight)
        assertEquals(8, draft?.reps)
        assertEquals(SetTypeUiModel.WORK, draft?.type)
        assertEquals(false, draft?.isDone)
    }

    @Test
    fun `OnSetTypeSelect with existing draft preserves draft weight and reps when chip cycles`() {
        // Defence-in-depth: if a draft already exists with edited fields, a chip click
        // must preserve them and only advance the type. This is the regression the
        // manual fix addressed.
        val stateFlow = stateWithPlan(SetTypeUiModel.WORK)
        val input = inputHandler(stateFlow)
        val click = clickHandler(stateFlow)

        input.invoke(Action.Input.OnSetWeightChange(PE_UUID, position = 0, value = 87.5))
        input.invoke(Action.Input.OnSetRepsChange(PE_UUID, position = 0, value = 12))
        click.invoke(Action.Click.OnSetTypeSelect(PE_UUID, position = 0, type = SetTypeUiModel.WORK))

        val draft = stateFlow.value.setDrafts[State.DraftKey(PE_UUID, 0)]
        assertEquals(87.5, draft?.weight)
        assertEquals(12, draft?.reps)
        assertEquals(SetTypeUiModel.FAILURE, draft?.type)
    }

    @Test
    fun `OnAddSet creates a draft at next position seeded from last known set`() {
        val stateFlow = MutableStateFlow(
            baseState(
                exercise = exerciseWithPlan(
                    plan = persistentListOf(
                        PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
                    ),
                ),
            ),
        )
        val handler = clickHandler(stateFlow)

        handler.invoke(Action.Click.OnAddSet(PE_UUID))

        val draft = stateFlow.value.setDrafts[State.DraftKey(PE_UUID, 1)]
        assertNotNull(draft)
        assertEquals(1, draft?.position)
        assertEquals(100.0, draft?.weight)
        assertEquals(5, draft?.reps)
        assertEquals(SetTypeUiModel.WORK, draft?.type)
        assertEquals(false, draft?.isDone)
    }

    @Test
    fun `OnAddSet on an exercise with no plan or performed seeds an empty fallback row`() {
        val stateFlow = MutableStateFlow(
            baseState(
                exercise = exerciseWithPlan(plan = persistentListOf()),
            ),
        )
        val handler = clickHandler(stateFlow)

        handler.invoke(Action.Click.OnAddSet(PE_UUID))

        val draft = stateFlow.value.setDrafts[State.DraftKey(PE_UUID, 0)]
        assertNotNull(draft)
        assertEquals(0, draft?.position)
        assertNull(draft?.weight)
        assertEquals(0, draft?.reps)
        assertEquals(SetTypeUiModel.WORK, draft?.type)
        assertEquals(false, draft?.isDone)
    }

    @Test
    fun `OnSetMarkDone clears the draft and adds a performed set with draft fields`() {
        val stateFlow = stateWithPlan(SetTypeUiModel.WORK)
        val click = clickHandler(stateFlow)
        val input = inputHandler(stateFlow)

        // Build a draft via input handler so we can verify it is cleared on mark-done.
        input.invoke(Action.Input.OnSetWeightChange(PE_UUID, position = 0, value = 110.0))
        input.invoke(Action.Input.OnSetRepsChange(PE_UUID, position = 0, value = 6))
        click.invoke(Action.Click.OnSetMarkDone(PE_UUID, position = 0))

        val draft = stateFlow.value.setDrafts[State.DraftKey(PE_UUID, 0)]
        assertNull(draft, "draft must be cleared after mark-done so the row reads from performed")

        val performed = stateFlow.value.exercises
            .first { it.performedExerciseUuid == PE_UUID }
            .performedSets
            .firstOrNull { it.position == 0 }
        assertNotNull(performed)
        assertEquals(110.0, performed?.weight)
        assertEquals(6, performed?.reps)
        assertEquals(SetTypeUiModel.WORK, performed?.type)
        assertEquals(true, performed?.isDone)
    }

    @Test
    fun `OnSetUncheck removes the performed set so the row falls back to plan or draft`() {
        // Pre-condition: row at position 0 is performed (isDone=true).
        val stateFlow = MutableStateFlow(
            baseState(
                exercise = exerciseWithPlan(
                    plan = persistentListOf(
                        PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
                    ),
                    performed = persistentListOf(
                        LiveSetUiModel(
                            position = 0,
                            weight = 110.0,
                            reps = 6,
                            type = SetTypeUiModel.WORK,
                            isDone = true,
                        ),
                    ),
                ),
            ),
        )
        val handler = clickHandler(stateFlow)

        handler.invoke(Action.Click.OnSetUncheck(PE_UUID, position = 0))

        val performed = stateFlow.value.exercises
            .first { it.performedExerciseUuid == PE_UUID }
            .performedSets
            .firstOrNull { it.position == 0 }
        assertNull(performed, "performed set at position 0 must be removed on uncheck")
    }

    private fun stateWithPlan(planType: SetTypeUiModel): MutableStateFlow<State> = MutableStateFlow(
        baseState(
            exercise = exerciseWithPlan(
                plan = persistentListOf(
                    PlanSetUiModel(weight = 100.0, reps = 5, type = planType),
                ),
            ),
        ),
    )

    private fun baseState(exercise: LiveExerciseUiModel): State = State.create(
        sessionUuid = "session-1",
        trainingUuid = "training-1",
    ).copy(
        isLoading = false,
        exercises = persistentListOf(exercise),
    )

    private fun exerciseWithPlan(
        plan: kotlinx.collections.immutable.ImmutableList<PlanSetUiModel>,
        performed: kotlinx.collections.immutable.ImmutableList<LiveSetUiModel> = persistentListOf(),
    ): LiveExerciseUiModel = LiveExerciseUiModel(
        performedExerciseUuid = PE_UUID,
        exerciseUuid = "ex-1",
        exerciseName = "Bench Press",
        exerciseType = ExerciseTypeUiModel.WEIGHTED,
        position = 0,
        status = ExerciseStatusUiModel.CURRENT,
        statusLabel = "",
        planSets = plan,
        performedSets = performed,
    )

    private fun handlerStore(stateFlow: MutableStateFlow<State>): LiveWorkoutHandlerStore =
        mockk(relaxed = true) {
            every { state } returns stateFlow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
        }

    private fun inputHandler(stateFlow: MutableStateFlow<State>) = InputHandler(handlerStore(stateFlow))

    private fun clickHandler(stateFlow: MutableStateFlow<State>) = ClickHandler(
        interactor = interactor,
        resourceWrapper = resourceWrapper,
        pickerHandler = pickerHandler,
        setMutator = setMutator,
        store = handlerStore(stateFlow),
    )

    private companion object {
        const val PE_UUID = "pe-1"
    }
}
