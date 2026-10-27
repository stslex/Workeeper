// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.domain.model.AddExerciseResult
import io.github.stslex.workeeper.feature.live_workout.domain.model.AdhocSessionResult
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExercisePickerEntry
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.FinishResult
import io.github.stslex.workeeper.feature.live_workout.domain.model.InlineAdhocResult
import io.github.stslex.workeeper.feature.live_workout.domain.model.PersonalRecordDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionSnapshotDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.StateStatusMapper
import io.github.stslex.workeeper.feature.live_workout.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.github.stslex.workeeper.feature.live_workout.resources.Res
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_picker_create_format
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_picker_no_match_format
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ExercisePickerHandlerTest {

    private val interactor = RecordingInteractor()
    private val stringResolver = RelaxedStringResolver()
    private val statusMapper = StateStatusMapper()

    @Test
    fun `OnDismiss flips picker to Hidden`() {
        val state = MutableStateFlow(stateWithVisiblePicker())

        handler(state).invoke(ExercisePickerAction.OnDismiss)

        assertEquals(BottomSheetState.Hidden, state.value.bottomSheetState)
    }

    @Test
    fun `OnQueryChange surfaces query immediately and dispatches a load coroutine`() {
        val state = MutableStateFlow(stateWithVisiblePicker())
        val store = handlerStore(state)

        ExercisePickerHandler(interactor, stringResolver, statusMapper, store)
            .invoke(ExercisePickerAction.OnQueryChange("bench"))

        val visible = state.value.bottomSheetState as BottomSheetState.ExercisePicker
        assertEquals("bench", visible.query)
        assertTrue(store.launches > 0, "the query dispatches its load")
    }

    @Test
    fun `OnExerciseSelect is no-op when add is already in flight`() {
        val state = MutableStateFlow(
            stateWithVisiblePicker().copy(isAddExerciseInFlight = true),
        )
        val store = handlerStore(state)

        ExercisePickerHandler(interactor, stringResolver, statusMapper, store)
            .invoke(ExercisePickerAction.OnExerciseSelect("u1"))

        assertEquals(
            0,
            interactor.count(
                "addExerciseToActiveSession",
                RecordingInteractor.ANY,
                RecordingInteractor.ANY,
                RecordingInteractor.ANY,
                true,
            ),
        )
    }

    @Test
    fun `OnExerciseSelect with finish-in-flight is also blocked`() {
        val state = MutableStateFlow(
            stateWithVisiblePicker().copy(isFinishInFlight = true),
        )
        val store = handlerStore(state)

        ExercisePickerHandler(interactor, stringResolver, statusMapper, store)
            .invoke(ExercisePickerAction.OnExerciseSelect("u1"))

        assertEquals(
            0,
            interactor.count(
                "addExerciseToActiveSession",
                RecordingInteractor.ANY,
                RecordingInteractor.ANY,
                RecordingInteractor.ANY,
                true,
            ),
        )
    }

    @Test
    fun `OnCreateNewExercise with blank name is no-op`() {
        val state = MutableStateFlow(stateWithVisiblePicker())
        val store = handlerStore(state)

        ExercisePickerHandler(interactor, stringResolver, statusMapper, store)
            .invoke(ExercisePickerAction.OnCreateNewExercise("   "))

        assertEquals(0, interactor.count("createInlineAdhocExercise", RecordingInteractor.ANY))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun onCreateNewExercise_freshAdhoc_skipsPrBaseline() = runTest {
        val state = MutableStateFlow(stateWithVisiblePicker())
        interactor.onCall("createInlineAdhocExercise", "Skull Crushers") {
            InlineAdhocResult(
                exerciseUuid = "ex-inline-1",
                name = "Skull Crushers",
                type = ExerciseTypeDomain.WEIGHTED,
                reusedExisting = false,
            )
        }
        interactor.onCall("addExerciseToActiveSession", "session-1", "training-1", "ex-inline-1", true) {
            AddExerciseResult(
                performedExerciseUuid = "pe-inline-1",
                planSets = null,
                isPlanAttached = true,
            )
        }

        val handler = ExercisePickerHandler(
            interactor = interactor,
            stringResolver = stringResolver,
            statusMapper = statusMapper,
            store = ExecutingLiveWorkoutHandlerStore(state, this),
        )

        handler.invoke(ExercisePickerAction.OnCreateNewExercise("Skull Crushers"))
        advanceUntilIdle()

        assertEquals(
            listOf("ex-inline-1"),
            state.value.exercises.map { it.exerciseUuid },
        )
        // reusedExisting = false → no history → PR baseline fetch is skipped.
        assertEquals(0, interactor.count("fetchPrSnapshotForExercise", RecordingInteractor.ANY))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun onCreateNewExercise_collisionWithLibrary_fetchesPrBaseline() = runTest {
        val state = MutableStateFlow(stateWithVisiblePicker())
        interactor.onCall("createInlineAdhocExercise", "Bench Press") {
            InlineAdhocResult(
                exerciseUuid = "ex-library-1",
                name = "Bench Press",
                type = ExerciseTypeDomain.WEIGHTED,
                reusedExisting = true,
            )
        }
        interactor.onCall("addExerciseToActiveSession", "session-1", "training-1", "ex-library-1", true) {
            AddExerciseResult(
                performedExerciseUuid = "pe-library-1",
                planSets = null,
                isPlanAttached = true,
            )
        }

        val handler = ExercisePickerHandler(
            interactor = interactor,
            stringResolver = stringResolver,
            statusMapper = statusMapper,
            store = ExecutingLiveWorkoutHandlerStore(state, this),
        )

        handler.invoke(ExercisePickerAction.OnCreateNewExercise("Bench Press"))
        advanceUntilIdle()

        // reusedExisting = true → existing row may have history → PR baseline is fetched.
        assertEquals(1, interactor.count("fetchPrSnapshotForExercise", "ex-library-1"))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun loadResults_partialMatch_showsCreateCta() = runTest {
        val state = MutableStateFlow(stateWithVisiblePicker())
        interactor.onCall("searchExercisesForPicker", "жим груди", emptySet<String>()) {
            listOf(
                ExercisePickerEntry(
                    uuid = "u-incline",
                    name = "жим груди под наклоном",
                    type = ExerciseTypeDomain.WEIGHTED,
                ),
            )
        }
        stringResolver.answer(
            Res.string.feature_live_workout_picker_create_format,
            "жим груди",
            value = "Create \"жим груди\"",
        )

        val handler = ExercisePickerHandler(
            interactor = interactor,
            stringResolver = stringResolver,
            statusMapper = statusMapper,
            store = ExecutingLiveWorkoutHandlerStore(state, this),
        )
        handler.invoke(ExercisePickerAction.OnQueryChange("жим груди"))
        advanceUntilIdle()

        val visible = state.value.bottomSheetState as BottomSheetState.ExercisePicker
        assertEquals("Create \"жим груди\"", visible.createCtaLabel)
        // Partial-match case: noMatchHeadline must stay null because results is non-empty.
        assertEquals(null, visible.noMatchHeadline)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun loadResults_exactMatchCaseInsensitive_hidesCreateCta() = runTest {
        val state = MutableStateFlow(stateWithVisiblePicker())
        interactor.onCall("searchExercisesForPicker", "BENCH PRESS", emptySet<String>()) {
            listOf(
                ExercisePickerEntry(
                    uuid = "u-bench",
                    name = "Bench Press",
                    type = ExerciseTypeDomain.WEIGHTED,
                ),
            )
        }

        val handler = ExercisePickerHandler(
            interactor = interactor,
            stringResolver = stringResolver,
            statusMapper = statusMapper,
            store = ExecutingLiveWorkoutHandlerStore(state, this),
        )
        handler.invoke(ExercisePickerAction.OnQueryChange("BENCH PRESS"))
        advanceUntilIdle()

        val visible = state.value.bottomSheetState as BottomSheetState.ExercisePicker
        // Exact case-insensitive match → CTA suppressed (DB-level dedupe would kick in).
        assertEquals(null, visible.createCtaLabel)
        assertEquals(null, visible.noMatchHeadline)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun loadResults_emptyResults_showsHeadlineAndCta() = runTest {
        val state = MutableStateFlow(stateWithVisiblePicker())
        interactor.onCall("searchExercisesForPicker", "skull crushers", emptySet<String>()) {
            emptyList<ExercisePickerEntry>()
        }
        stringResolver.answer(
            Res.string.feature_live_workout_picker_no_match_format,
            "skull crushers",
            value = "No exercises match \"skull crushers\"",
        )
        stringResolver.answer(
            Res.string.feature_live_workout_picker_create_format,
            "skull crushers",
            value = "Create \"skull crushers\"",
        )

        val handler = ExercisePickerHandler(
            interactor = interactor,
            stringResolver = stringResolver,
            statusMapper = statusMapper,
            store = ExecutingLiveWorkoutHandlerStore(state, this),
        )
        handler.invoke(ExercisePickerAction.OnQueryChange("skull crushers"))
        advanceUntilIdle()

        val visible = state.value.bottomSheetState as BottomSheetState.ExercisePicker
        // Genuinely empty list — both indicators surface together (Q3: explicit no-match).
        assertEquals("No exercises match \"skull crushers\"", visible.noMatchHeadline)
        assertEquals("Create \"skull crushers\"", visible.createCtaLabel)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun loadResults_blankQuery_hidesBothLabels() = runTest {
        val state = MutableStateFlow(stateWithVisiblePicker())
        interactor.onCall("searchExercisesForPicker", "   ", emptySet<String>()) { emptyList<ExercisePickerEntry>() }

        val handler = ExercisePickerHandler(
            interactor = interactor,
            stringResolver = stringResolver,
            statusMapper = statusMapper,
            store = ExecutingLiveWorkoutHandlerStore(state, this),
        )
        handler.invoke(ExercisePickerAction.OnQueryChange("   "))
        advanceUntilIdle()

        val visible = state.value.bottomSheetState as BottomSheetState.ExercisePicker
        assertEquals(null, visible.noMatchHeadline)
        assertEquals(null, visible.createCtaLabel)
    }

    @Test
    fun `OnExerciseSelect for an unknown uuid does nothing`() {
        val state = MutableStateFlow(
            stateWithVisiblePicker().copy(
                bottomSheetState = BottomSheetState.ExercisePicker(
                    query = "",
                    results = persistentListOf(
                        ExercisePickerUiModel("u1", "Bench Press", ExerciseTypeUiModel.WEIGHTED),
                    ),
                    noMatchHeadline = null,
                    createCtaLabel = null,
                ),
            ),
        )
        val store = handlerStore(state)

        ExercisePickerHandler(interactor, stringResolver, statusMapper, store)
            .invoke(ExercisePickerAction.OnExerciseSelect("unknown-uuid"))

        assertEquals(
            0,
            interactor.count(
                "addExerciseToActiveSession",
                RecordingInteractor.ANY,
                RecordingInteractor.ANY,
                RecordingInteractor.ANY,
                true,
            ),
        )
    }

    private fun handler(stateFlow: MutableStateFlow<State>): ExercisePickerHandler =
        ExercisePickerHandler(
            interactor = interactor,
            stringResolver = stringResolver,
            statusMapper = statusMapper,
            store = handlerStore(stateFlow),
        )

    private fun handlerStore(stateFlow: MutableStateFlow<State>): RelaxedHandlerStore =
        RelaxedHandlerStore(stateFlow)

    private class ExecutingLiveWorkoutHandlerStore(
        private val stateFlow: MutableStateFlow<State>,
        private val scope: CoroutineScope,
    ) : LiveWorkoutHandlerStore {

        override val state: StateFlow<State> = stateFlow
        override val lastAction: io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action? =
            null
        override val logger: Logger = SilentLogger()

        override fun sendEvent(
            event: io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event,
        ) = Unit

        override fun consume(
            action: io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action,
        ) = Unit

        override suspend fun consumeOnMain(
            action: io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action,
        ) = Unit

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
        ): Job = scope.launch {
            try {
                val result = action()
                onSuccess(result)
            } catch (throwable: Throwable) {
                onError(throwable)
            }
        }

        override fun <T> launchDefault(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            action: suspend CoroutineScope.() -> T,
        ): Job = scope.launch {
            try {
                val result = action()
                onSuccess(result)
            } catch (throwable: Throwable) {
                onError(throwable)
            }
        }

        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Job = scope.launch {
            try {
                collect { each(it) }
            } catch (throwable: Throwable) {
                onError(throwable)
            }
        }
    }

    private fun stateWithVisiblePicker(): State = State.create(
        sessionUuid = "session-1",
        trainingUuid = "training-1",
    ).copy(
        isLoading = false,
        bottomSheetState = BottomSheetState.ExercisePicker(
            query = "",
            results = persistentListOf(),
            noMatchHeadline = null,
            createCtaLabel = null,
        ),
    )

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
