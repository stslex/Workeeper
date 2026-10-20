// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.domain.model.ActiveSessionDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.ExercisePlanDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.SessionDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TagDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingChangeDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingExerciseDetail
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingEditCopy
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.resources.Res
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_error_no_exercises
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_exercise_removed
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_plan_set_removed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `isLoading`'s lifecycle on the training editor. The consumer is `SingleTrainingGraph`'s
 * `if (state.isLoading) return@navComponentScreen`, which withholds the whole screen.
 */
internal class CommonHandlerTest {

    private val interactor = FakeSingleTrainingInteractor()
    private val resourceWrapper = MediumDateOnlyResourceWrapper()
    private val resolver = EditCopyStringResolver()

    /**
     * The store runs `launch` on the identity's `runTest` scheduler and routes a throw to `onError`
     * — that routing is the thing under test, since production's default `onError` is `{}`.
     */
    private fun setup(
        initialState: State,
        scope: CoroutineScope,
    ): Pair<MutableStateFlow<State>, CommonHandler> {
        val stateFlow = MutableStateFlow(initialState)
        val store = FakeCommonHandlerStore(stateFlow, scope)
        return stateFlow to CommonHandler(interactor, resourceWrapper, resolver, store)
    }

    @Test
    fun `a load that throws clears isLoading or the route is composed on nothing forever`() = runTest {
        interactor.training = { throw IllegalStateException("db down") }
        val (stateFlow, handler) = setup(State.create(uuid = "training-1"), this)
        // Precondition rather than assertion: a route with a uuid starts loading by construction.
        assertTrue(stateFlow.value.isLoading)

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertFalse(stateFlow.value.isLoading)
    }

    @Test
    fun `a training that is not there clears isLoading too`() = runTest {
        interactor.training = { null }
        val (stateFlow, handler) = setup(State.create(uuid = "training-1"), this)

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertFalse(stateFlow.value.isLoading)
    }

    /** §3.3: the head's count is the total of finished sessions, not the visible page. */
    @Test
    fun `the История count is the total not the visible page`() = runTest {
        interactor.training = { training }
        interactor.trainingExercises = { emptyList() }
        interactor.recentSessions = { emptyList() }
        interactor.sessionCount = { 12 }
        val (stateFlow, handler) = setup(State.create(uuid = "training-1"), this)

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertEquals(12, stateFlow.value.historyCount)
    }

    @Test
    fun `a create route never loads so it is never withheld and it opens clean`() = runTest {
        val (stateFlow, handler) = setup(State.create(uuid = null), this)

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertFalse(stateFlow.value.isLoading)
        // The create branch takes its dirty baseline here, so a new training opens clean.
        assertNotNull(stateFlow.value.originalSnapshot)
        assertFalse(stateFlow.value.hasChanges)
    }

    /** D2: `Init` resolves the editor's constant copy once per Store, on either route. */
    @Test
    fun `Init resolves the editor's constant copy into State`() = runTest {
        val (stateFlow, handler) = setup(State.create(uuid = null), this)

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertEquals(
            TrainingEditCopy(
                noExercisesError = "no-exercises",
                exerciseRemoved = "exercise-removed",
                setRemoved = "set-removed",
            ),
            stateFlow.value.editCopy,
        )
    }

    /**
     * The store as the MockK store answered it: [state] is the identity's flow, [updateState] and
     * the update-lambda [updateStateImmediate] apply to it, and [launch] launches on the identity's
     * `runTest` scheduler, runs the action inside `supervisorScope`, then `onSuccess`, and routes a
     * throw from either to `onError`. [Flow.launch] records and collects nothing, as the relaxed
     * mock did. Every other member fails fast.
     */
    private class FakeCommonHandlerStore(
        private val flow: MutableStateFlow<State>,
        private val scope: CoroutineScope,
    ) : SingleTrainingHandlerStore {

        val observed = mutableListOf<Flow<*>>()

        override val state: StateFlow<State> = flow

        override val lastAction: Nothing
            get() = error("lastAction must not be read by CommonHandler")

        override val logger: Logger
            get() = error("logger must not be read by CommonHandler")

        override fun sendEvent(event: Event): Nothing = error("sendEvent must not be used by CommonHandler")

        override fun consume(action: Action): Nothing = error("consume must not be used by CommonHandler")

        override suspend fun consumeOnMain(action: Action): Nothing =
            error("consumeOnMain must not be used by CommonHandler")

        override fun updateState(update: (State) -> State) {
            flow.value = update(flow.value)
        }

        override suspend fun updateStateImmediate(update: suspend (State) -> State) {
            flow.value = update(flow.value)
        }

        override suspend fun updateStateImmediate(state: State): Nothing =
            error("updateStateImmediate(state) must not be used by CommonHandler")

        override fun <T> launch(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            action: suspend CoroutineScope.() -> T,
        ): Job = scope.launch {
            try {
                val result = supervisorScope { action() }
                onSuccess(result)
            } catch (t: Throwable) {
                onError(t)
            }
        }

        override fun <T> launchDefault(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            action: suspend CoroutineScope.() -> T,
        ): Nothing = error("launchDefault must not be used by CommonHandler")

        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Job {
            observed.add(this)
            return Job()
        }
    }

    /**
     * The interactor as the relaxed MockK one answered it: the load's five reads answer what the
     * identity set up — a value or a throw — and otherwise the relaxed default (`null`, an empty
     * list, `0`, `false`); the two observed flows answer as they were stubbed. A member the load
     * never reaches throws. Every call is counted in [calls].
     */
    private class FakeSingleTrainingInteractor : SingleTrainingInteractor {

        var training: suspend () -> TrainingDomain? = { null }
        var trainingExercises: suspend () -> List<TrainingExerciseDetail> = { emptyList() }
        var recentSessions: suspend () -> List<SessionDomain> = { emptyList() }
        var sessionCount: suspend () -> Int = { 0 }
        var permanentlyDeletable: suspend () -> Boolean = { false }
        val calls = mutableListOf<String>()

        override suspend fun getTraining(uuid: String): TrainingDomain? = called("getTraining", training)

        override suspend fun getTrainingExercises(trainingUuid: String): List<TrainingExerciseDetail> =
            called("getTrainingExercises", trainingExercises)

        override suspend fun getRecentSessions(trainingUuid: String, limit: Int): List<SessionDomain> =
            called("getRecentSessions", recentSessions)

        override suspend fun countSessions(trainingUuid: String): Int = called("countSessions", sessionCount)

        override fun observeAvailableTags(): Flow<List<TagDomain>> {
            calls.add("observeAvailableTags")
            return flowOf(emptyList())
        }

        override suspend fun saveTraining(snapshot: TrainingChangeDomain, plans: List<ExercisePlanDomain>): Nothing =
            error("saveTraining must not be used by CommonHandler")

        override suspend fun createTag(name: String): Nothing = error("createTag must not be used by CommonHandler")

        override suspend fun archive(uuid: String): Nothing = error("archive must not be used by CommonHandler")

        override suspend fun permanentlyDelete(uuid: String): Nothing =
            error("permanentlyDelete must not be used by CommonHandler")

        override suspend fun canPermanentlyDelete(uuid: String): Boolean =
            called("canPermanentlyDelete", permanentlyDeletable)

        override fun observeAnyActiveSession(): Flow<ActiveSessionDomain?> {
            calls.add("observeAnyActiveSession")
            return emptyFlow()
        }

        override suspend fun searchExercisesForPicker(
            query: String,
            excludeUuids: Set<String>,
        ): Nothing = error("searchExercisesForPicker must not be used by CommonHandler")

        override suspend fun resolveExercises(uuids: List<String>): Nothing =
            error("resolveExercises must not be used by CommonHandler")

        override suspend fun resolveStartSessionConflict(requestedTrainingUuid: String): Nothing =
            error("resolveStartSessionConflict must not be used by CommonHandler")

        override suspend fun deleteSession(sessionUuid: String): Nothing =
            error("deleteSession must not be used by CommonHandler")

        override suspend fun getLabels(exerciseUuid: String): Nothing =
            error("getLabels must not be used by CommonHandler")

        private suspend fun <T> called(member: String, answer: suspend () -> T): T {
            calls.add(member)
            return answer()
        }
    }

    /** The relaxed mock answered `formatMediumDate` with `""`; nothing else is reached. */
    private class MediumDateOnlyResourceWrapper : ResourceWrapper {

        override fun getString(id: Int, vararg args: Any): String = error("not used")

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String =
            error("not used")

        override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String =
            error("not used")

        override fun formatMediumDate(timestamp: Long): String = ""

        override fun formatDayMonth(timestamp: Long): String = error("not used")
    }

    /**
     * Strict: answers the three reads of `Init`'s edit copy (D2), each with no argument, with a
     * distinct string per field; any other resource or argument list throws. A throw inside the
     * launch routes to `onError`, so a missed read leaves `editCopy` at `TrainingEditCopy.EMPTY`.
     */
    private class EditCopyStringResolver : StringResolver {

        override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String {
            check(formatArgs.isEmpty()) { "$resource is read verbatim; got ${formatArgs.toList()}" }
            return when (resource) {
                Res.string.feature_training_edit_error_no_exercises -> "no-exercises"
                Res.string.feature_training_edit_exercise_removed -> "exercise-removed"
                Res.string.feature_training_edit_plan_set_removed -> "set-removed"
                else -> error("CommonHandler must not read $resource")
            }
        }

        override suspend fun getPluralString(
            resource: PluralStringResource,
            quantity: Int,
            vararg formatArgs: Any,
        ): String = error("CommonHandler reads no plural; got $resource")
    }

    private companion object {

        /** A real training in place of the relaxed `mockk()` the History identity used. */
        val training = TrainingDomain(
            uuid = "training-1",
            name = "",
            description = null,
            isAdhoc = false,
            archived = false,
            archivedAt = null,
            timestamp = 0L,
            labels = emptyList(),
            exerciseUuids = emptyList(),
        )
    }
}
