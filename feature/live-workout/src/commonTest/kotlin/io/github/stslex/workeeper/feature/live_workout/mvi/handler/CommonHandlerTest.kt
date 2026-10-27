// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_unit_reps
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExternalWriteDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionSnapshotDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionStateDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveSetMutator
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.StateStatusMapper
import io.github.stslex.workeeper.feature.live_workout.mvi.model.NAMED_COPY
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_cancel_body
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_cancel_confirm
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_cancel_dismiss
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_cancel_title
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_session_progress_format
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_session_unnamed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_empty_finish_continue
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_empty_finish_discard
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_finish_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_invalid_reps
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_name_label
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_name_required
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_pr_weighted_format
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_pr_weightless_format
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_stat_exercises_format
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_stat_exercises_with_skipped_format
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_stat_sets_count
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_meta_format
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_meta_skipped_format
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_reset_body
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_reset_confirm
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_reset_dismiss
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_reset_title
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_status_no_plan
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_status_skipped
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_toast_exercise_removed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_toast_exercise_removed_from_workout
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_toast_set_added
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_toast_set_removed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_training_name_placeholder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.github.stslex.workeeper.feature.live_workout.resources.Res as LiveWorkoutRes

@OptIn(ExperimentalCoroutinesApi::class)
internal class CommonHandlerTest {

    private val interactor = LoadingInteractor()
    private val setMutator = LiveSetMutator(StateStatusMapper())
    private val dispatchers = StoreDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined)

    /**
     * The store fake runs `launch` on the identity's scheduler and routes a throw to `onError`,
     * because that routing is the thing under test — a fake that swallowed it would report the
     * defect fixed.
     */
    private fun TestScope.setup(
        initialState: State,
        stringResolver: StringResolver = CatalogStringResolver(),
    ): Pair<MutableStateFlow<State>, CommonHandler> {
        val stateFlow = MutableStateFlow(initialState)
        val store = FakeCommonHandlerStore(stateFlow, backgroundScope)
        val handler = CommonHandler(interactor, stringResolver, setMutator, dispatchers, store)
        return stateFlow to handler
    }

    /** The load-failure exit on both arms; both flags asserted, since either alone lies. */
    @Test
    fun `a load that throws clears isLoading and records the failure`() = runTest {
        interactor.load = { throw IllegalStateException("db down") }
        val (stateFlow, handler) = setup(
            State.create(sessionUuid = "session-1", trainingUuid = "training-1"),
        )
        // Precondition rather than assertion: a session route starts loading by construction.
        assertTrue(stateFlow.value.isLoading)

        handler.invoke(Action.Common.Init)
        runCurrent()

        assertFalse(stateFlow.value.isLoading)
        assertTrue(stateFlow.value.loadFailed)
    }

    @Test
    fun `a session that is not there is abandoned the same way not shown as empty`() = runTest {
        interactor.load = { null }
        val (stateFlow, handler) = setup(
            State.create(sessionUuid = "session-1", trainingUuid = "training-1"),
        )

        handler.invoke(Action.Common.Init)
        runCurrent()

        assertFalse(stateFlow.value.isLoading)
        assertTrue(stateFlow.value.loadFailed)
    }

    @Test
    fun `Init launches a load coroutine`() = runTest {
        val stateFlow = MutableStateFlow(
            State.create(sessionUuid = "session-1", trainingUuid = "training-1"),
        )
        val store = FakeCommonHandlerStore(stateFlow, backgroundScope)
        val handler = CommonHandler(
            interactor = interactor,
            stringResolver = CatalogStringResolver(),
            setMutator = setMutator,
            storeDispatchers = dispatchers,
            store = store,
        )

        handler.invoke(Action.Common.Init)

        assertTrue(store.launches > 0, "Init launches its load")
    }

    /** D8: the copy resolves at the load, each field from its own key, and nothing else is read. */
    @Test
    fun `a load resolves every copy field from its own catalog key`() = runTest {
        interactor.load = { snapshot() }
        val resolver = CatalogStringResolver()
        val (stateFlow, handler) = setup(
            State.create(sessionUuid = "session-1", trainingUuid = "training-1"),
            stringResolver = resolver,
        )

        handler.invoke(Action.Common.Init)
        runCurrent()

        assertFalse(stateFlow.value.loadFailed)
        assertEquals(NAMED_COPY, stateFlow.value.liveCopy)
        assertEquals(CATALOG.size, resolver.reads.size, "each key is read once: ${resolver.reads}")
        assertEquals(CATALOG.keys, resolver.reads.toSet())
    }

    /** An in-progress session of training `training-1` named `Push Day`, with no exercises. */
    private fun snapshot() = SessionSnapshotDomain(
        session = SessionDomain(
            uuid = "session-1",
            trainingUuid = "training-1",
            state = SessionStateDomain.IN_PROGRESS,
            startedAt = 1_000L,
            finishedAt = null,
        ),
        trainingName = "Push Day",
        isAdhoc = false,
        exercises = emptyList(),
        preSessionPrSnapshot = emptyMap(),
    )

    /**
     * Answers exactly the copy's 31 keys, each with [NAMED_COPY]'s value for the field that key
     * belongs to, verbatim, and records every read; any other read, or a read with arguments, throws.
     */
    private class CatalogStringResolver : StringResolver {

        val reads = mutableListOf<StringResource>()

        override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String {
            check(formatArgs.isEmpty()) { "$resource is read verbatim; got ${formatArgs.toList()}" }
            reads += resource
            return CATALOG[resource] ?: error("$resource is not part of the copy")
        }

        override suspend fun getPluralString(
            resource: PluralStringResource,
            quantity: Int,
            vararg formatArgs: Any,
        ): Nothing = error("the copy reads no plural; got $resource")
    }

    /**
     * Launches on the identity's scheduler inside `supervisorScope`, then `onSuccess`, a throw to
     * `onError`. CommonHandler writes only through [updateState]; every other member fails fast.
     */
    private class FakeCommonHandlerStore(
        override val state: MutableStateFlow<State>,
        private val scope: CoroutineScope,
    ) : LiveWorkoutHandlerStore {

        var launches: Int = 0
            private set

        override val lastAction: Nothing get() = unreached("lastAction")

        override val logger: Nothing get() = unreached("logger")

        override fun sendEvent(event: Event): Nothing = unreached("sendEvent")

        override fun consume(action: Action): Nothing = unreached("consume")

        override suspend fun consumeOnMain(action: Action): Nothing = unreached("consumeOnMain")

        override fun updateState(update: (State) -> State) {
            state.value = update(state.value)
        }

        override suspend fun updateStateImmediate(update: suspend (State) -> State): Nothing =
            unreached("updateStateImmediate")

        override suspend fun updateStateImmediate(state: State): Nothing = unreached("updateStateImmediate")

        override fun <T> launch(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            action: suspend CoroutineScope.() -> T,
        ): Job {
            launches += 1
            return scope.launch {
                runCatching { supervisorScope { action() } }
                    .onSuccess { onSuccess(this, it) }
                    .onFailure { onError(it) }
            }
        }

        override fun <T> launchDefault(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            action: suspend CoroutineScope.() -> T,
        ): Nothing = unreached("launchDefault")

        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Nothing = unreached("Flow.launch")

        private fun unreached(member: String): Nothing = error("CommonHandler does not reach $member")
    }

    /** Answers [load] and an empty write flow; every other member fails fast. */
    private class LoadingInteractor : LiveWorkoutInteractor {

        var load: suspend (String) -> SessionSnapshotDomain? = { unreached("loadSession") }

        override suspend fun loadSession(sessionUuid: String): SessionSnapshotDomain? = load(sessionUuid)

        override fun observeExternalWrites(sessionUuid: String): Flow<ExternalWriteDomain> = emptyFlow()

        override suspend fun startSession(trainingUuid: String): Nothing = unreached("startSession")

        override suspend fun createAdhocSession(name: String, exerciseUuids: List<String>): Nothing =
            unreached("createAdhocSession")

        override suspend fun addExerciseToActiveSession(
            sessionUuid: String,
            trainingUuid: String,
            exerciseUuid: String,
            attachToPlan: Boolean,
        ): Nothing = unreached("addExerciseToActiveSession")

        override suspend fun discardAdhocSession(sessionUuid: String, trainingUuid: String): Nothing =
            unreached("discardAdhocSession")

        override suspend fun createInlineAdhocExercise(name: String): Nothing = unreached("createInlineAdhocExercise")

        override suspend fun updateTrainingName(trainingUuid: String, name: String): Nothing =
            unreached("updateTrainingName")

        override suspend fun searchExercisesForPicker(query: String, excludedUuids: Set<String>): Nothing =
            unreached("searchExercisesForPicker")

        override suspend fun fetchPrSnapshotForExercise(exerciseUuid: String): Nothing =
            unreached("fetchPrSnapshotForExercise")

        override suspend fun upsertSet(performedExerciseUuid: String, position: Int, set: PlanSetDomain): Nothing =
            unreached("upsertSet")

        override suspend fun deleteSet(performedExerciseUuid: String, position: Int): Nothing = unreached("deleteSet")

        override suspend fun setSkipped(performedExerciseUuid: String, skipped: Boolean): Nothing =
            unreached("setSkipped")

        override suspend fun deleteExerciseFromSession(
            performedExerciseUuid: String,
            exerciseUuid: String,
            trainingUuid: String?,
            removeFromPlan: Boolean,
        ): Nothing = unreached("deleteExerciseFromSession")

        override suspend fun setPlanAttachment(
            trainingUuid: String,
            exerciseUuid: String,
            attached: Boolean,
            planSets: List<PlanSetDomain>?,
        ): Nothing = unreached("setPlanAttachment")

        override suspend fun resetExerciseSets(performedExerciseUuid: String): Nothing =
            unreached("resetExerciseSets")

        override suspend fun finishSession(sessionUuid: String, newTrainingName: String?): Nothing =
            unreached("finishSession")

        override suspend fun cancelSession(sessionUuid: String): Nothing = unreached("cancelSession")

        override suspend fun setPlanForExercise(
            trainingUuid: String,
            exerciseUuid: String,
            plan: List<PlanSetDomain>?,
        ): Nothing = unreached("setPlanForExercise")

        override suspend fun setAdhocPlan(exerciseUuid: String, plan: List<PlanSetDomain>?): Nothing =
            unreached("setAdhocPlan")

        private fun unreached(member: String): Nothing = error("CommonHandler's identities do not reach $member")
    }

    private companion object {

        /** The copy's 31 keys, each to its field's [NAMED_COPY] value, in `resolveCopy`'s order. */
        val CATALOG: Map<StringResource, String> = linkedMapOf(
            LiveWorkoutRes.string.feature_live_workout_training_name_placeholder to
                NAMED_COPY.trainingNamePlaceholder,
            LiveWorkoutRes.string.feature_live_workout_status_skipped to NAMED_COPY.statusSkipped,
            LiveWorkoutRes.string.feature_live_workout_status_no_plan to NAMED_COPY.statusNoPlan,
            Res.string.core_ui_kit_plan_editor_unit_reps to NAMED_COPY.repsUnit,
            LiveWorkoutRes.string.feature_live_workout_meta_format to NAMED_COPY.metaTemplate,
            LiveWorkoutRes.string.feature_live_workout_meta_skipped_format to NAMED_COPY.metaSkippedTemplate,
            LiveWorkoutRes.string.feature_live_workout_reset_title to NAMED_COPY.resetTitle,
            LiveWorkoutRes.string.feature_live_workout_reset_body to NAMED_COPY.resetBody,
            LiveWorkoutRes.string.feature_live_workout_reset_confirm to NAMED_COPY.resetConfirm,
            LiveWorkoutRes.string.feature_live_workout_reset_dismiss to NAMED_COPY.resetDismiss,
            LiveWorkoutRes.string.feature_live_workout_empty_finish_discard to NAMED_COPY.emptyFinishDiscard,
            LiveWorkoutRes.string.feature_live_workout_empty_finish_continue to NAMED_COPY.emptyFinishContinue,
            LiveWorkoutRes.string.feature_live_workout_cancel_title to NAMED_COPY.cancelTitle,
            LiveWorkoutRes.string.feature_live_workout_cancel_body to NAMED_COPY.cancelBody,
            LiveWorkoutRes.string.feature_live_workout_cancel_confirm to NAMED_COPY.cancelConfirm,
            LiveWorkoutRes.string.feature_live_workout_cancel_dismiss to NAMED_COPY.cancelDismiss,
            LiveWorkoutRes.string.feature_live_workout_delete_session_unnamed to NAMED_COPY.deleteSessionUnnamed,
            LiveWorkoutRes.string.feature_live_workout_delete_session_progress_format to
                NAMED_COPY.deleteSessionProgressTemplate,
            LiveWorkoutRes.string.feature_live_workout_finish_name_label to NAMED_COPY.finishNameLabel,
            LiveWorkoutRes.string.feature_live_workout_finish_name_required to NAMED_COPY.finishNameRequired,
            LiveWorkoutRes.string.feature_live_workout_finish_stat_sets_count to NAMED_COPY.finishSetsCountTemplate,
            LiveWorkoutRes.string.feature_live_workout_finish_stat_exercises_format to
                NAMED_COPY.finishExercisesTemplate,
            LiveWorkoutRes.string.feature_live_workout_finish_stat_exercises_with_skipped_format to
                NAMED_COPY.finishExercisesWithSkippedTemplate,
            LiveWorkoutRes.string.feature_live_workout_finish_pr_weighted_format to
                NAMED_COPY.finishPrWeightedTemplate,
            LiveWorkoutRes.string.feature_live_workout_finish_pr_weightless_format to
                NAMED_COPY.finishPrWeightlessTemplate,
            LiveWorkoutRes.string.feature_live_workout_toast_set_added to NAMED_COPY.toastSetAdded,
            LiveWorkoutRes.string.feature_live_workout_toast_set_removed to NAMED_COPY.toastSetRemoved,
            LiveWorkoutRes.string.feature_live_workout_toast_exercise_removed to
                NAMED_COPY.toastExerciseRemovedTemplate,
            LiveWorkoutRes.string.feature_live_workout_toast_exercise_removed_from_workout to
                NAMED_COPY.toastExerciseRemovedFromWorkoutTemplate,
            LiveWorkoutRes.string.feature_live_workout_error_invalid_reps to NAMED_COPY.errorInvalidReps,
            LiveWorkoutRes.string.feature_live_workout_error_finish_failed to NAMED_COPY.errorFinishFailed,
        )
    }
}
