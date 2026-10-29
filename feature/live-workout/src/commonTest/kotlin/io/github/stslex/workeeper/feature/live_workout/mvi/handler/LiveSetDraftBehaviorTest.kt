// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
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
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveSetRowsResolver.withVisibleSets
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.StateStatusMapper
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Locks the draft seed/update invariant: every draft mutation keeps all unrelated fields from
 * the visible row (performed > draft > plan > fallback).
 */
internal class LiveSetDraftBehaviorTest {

    private val interactor = RecordingInteractor()
    private val stringResolver = RelaxedStringResolver()
    private val pickerHandler = FakeExercisePicker()
    private val statusMapper = StateStatusMapper()
    private val setMutator = LiveSetMutator(statusMapper)

    @Test
    fun `OnSetTypeSelect with no draft seeds from plan and preserves weight and reps`() {
        // Plan is weight=100.0, reps=5; a type chip click must keep both.
        val stateFlow = stateWithPlan(SetTypeUiModel.WORK)
        val handler = clickHandler(stateFlow)

        handler.invoke(Action.Click.OnSetTypeSelect(PE_UUID, position = 0, type = SetTypeUiModel.FAILURE))

        val draft = stateFlow.value.setDrafts[State.DraftKey(PE_UUID, 0)]
        assertNotNull(draft)
        assertEquals(100.0, draft?.weight)
        assertEquals(5, draft?.reps)
        assertEquals(SetTypeUiModel.DROP, draft?.type)
        assertEquals(false, draft?.isDone)
    }

    @Test
    fun `OnSetWeightChange with no draft seeds from plan and preserves type and reps`() {
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

        click.invoke(Action.Click.OnSetTypeSelect(PE_UUID, position = 0, type = SetTypeUiModel.FAILURE))
        // The following reps change must not lose the type the chip click just set.
        input.invoke(Action.Input.OnSetRepsChange(PE_UUID, position = 0, value = 8))

        val draft = stateFlow.value.setDrafts[State.DraftKey(PE_UUID, 0)]
        assertNotNull(draft)
        assertEquals(100.0, draft?.weight)
        assertEquals(8, draft?.reps)
        assertEquals(SetTypeUiModel.DROP, draft?.type)
        assertEquals(false, draft?.isDone)
    }

    @Test
    fun `OnSetTypeSelect after OnSetWeightChange preserves the edited weight`() {
        val stateFlow = stateWithPlan(SetTypeUiModel.WORK)
        val click = clickHandler(stateFlow)
        val input = inputHandler(stateFlow)

        input.invoke(Action.Input.OnSetWeightChange(PE_UUID, position = 0, value = 120.0))
        click.invoke(Action.Click.OnSetTypeSelect(PE_UUID, position = 0, type = SetTypeUiModel.FAILURE))

        val draft = stateFlow.value.setDrafts[State.DraftKey(PE_UUID, 0)]
        assertNotNull(draft)
        assertEquals(120.0, draft?.weight)
        assertEquals(5, draft?.reps)
        assertEquals(SetTypeUiModel.DROP, draft?.type)
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
        // A chip click on an existing edited draft preserves its fields and only advances type.
        val stateFlow = stateWithPlan(SetTypeUiModel.WORK)
        val input = inputHandler(stateFlow)
        val click = clickHandler(stateFlow)

        input.invoke(Action.Input.OnSetWeightChange(PE_UUID, position = 0, value = 87.5))
        input.invoke(Action.Input.OnSetRepsChange(PE_UUID, position = 0, value = 12))
        click.invoke(Action.Click.OnSetTypeSelect(PE_UUID, position = 0, type = SetTypeUiModel.FAILURE))

        val draft = stateFlow.value.setDrafts[State.DraftKey(PE_UUID, 0)]
        assertEquals(87.5, draft?.weight)
        assertEquals(12, draft?.reps)
        assertEquals(SetTypeUiModel.DROP, draft?.type)
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
    ).withVisibleSets()

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
        RelaxedHandlerStore(stateFlow)

    private fun inputHandler(stateFlow: MutableStateFlow<State>) = InputHandler(handlerStore(stateFlow))

    private fun clickHandler(stateFlow: MutableStateFlow<State>) = ClickHandler(
        interactor = interactor,
        stringResolver = stringResolver,
        pickerHandler = pickerHandler,
        setMutator = setMutator,
        store = handlerStore(stateFlow),
    )

    private companion object {
        const val PE_UUID = "pe-1"
    }

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
