// SPDX-License-Identifier: GPL-3.0-only
@file:Suppress("INVALID_CHARACTERS_NATIVE_ERROR")

package io.github.stslex.workeeper.feature.plan_editor.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorHandlerStore
import io.github.stslex.workeeper.feature.plan_editor.domain.PlanEditorInteractor
import io.github.stslex.workeeper.feature.plan_editor.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.plan_editor.domain.model.PlanEditorLoadResult
import io.github.stslex.workeeper.feature.plan_editor.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.plan_editor.domain.model.SetTypeDomain
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.ErrorType
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `isLoading`'s lifecycle: `PlanEditorGraph` withholds the whole screen while the flag is true,
 * so every path that sets it must clear it — the failure branch included.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CommonHandlerTest {

    private fun TestScope.setup(
        initial: State,
        outcome: LoadOutcome,
    ): TestSetup {
        val interactor = CommonHandlerInteractorFake(outcome)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = CommonHandlerStoreFake(initial, this, dispatcher)
        return TestSetup(store, interactor, CommonHandler(interactor, store))
    }

    private fun existingInitial(): State = State.init(
        mode = State.Mode.Exercise(exerciseUuid = "exercise-1"),
        seedType = ExerciseTypeUiModel.WEIGHTED,
        seedPlan = persistentListOf(),
    )

    private data class TestSetup(
        val store: CommonHandlerStoreFake,
        val interactor: CommonHandlerInteractorFake,
        val handler: CommonHandler,
    )

    @Test
    fun `a load that throws clears isLoading, or the route is composed on nothing forever`() =
        runTest {
            val (store, interactor, handler) = setup(
                existingInitial(),
                LoadOutcome.Failure(IllegalStateException("db down")),
            )
            // Precondition, not assertion: an Existing route starts loading by construction.
            assertTrue(store.state.value.isLoading)

            handler.invoke(Action.Common.Init)
            advanceUntilIdle()

            assertFalse(store.state.value.isLoading)
            assertEquals(
                listOf<Event>(Event.ShowError(ErrorType.LoadFailed)),
                store.events,
            )
            assertEquals(listOf("db down"), store.launchErrors.map { it.message })
            assertEquals(listOf(LoadRequest("exercise-1", null)), interactor.loadRequests)
        }

    @Test
    fun `NotFound clears isLoading and reports, same reason`() = runTest {
        val (store, interactor, handler) = setup(
            existingInitial(),
            LoadOutcome.Value(PlanEditorLoadResult.NotFound),
        )

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertFalse(store.state.value.isLoading)
        assertEquals(
            listOf<Event>(Event.ShowError(ErrorType.LoadFailed)),
            store.events,
        )
        assertEquals(emptyList(), store.launchErrors)
        assertEquals(listOf(LoadRequest("exercise-1", null)), interactor.loadRequests)
    }

    @Test
    fun `a successful load clears isLoading and hydrates the type the seed guessed wrong`() =
        runTest {
            // The seed is WEIGHTED for every Existing route, so this is the case the route gate
            // exists for: without it the toggle would draw WEIGHTED and flip in front of the user.
            val success = PlanEditorLoadResult.Success(
                exerciseName = "Подтягивания",
                type = ExerciseTypeDomain.WEIGHTLESS,
                plan = listOf(
                    PlanSetDomain(weight = null, reps = 8, type = SetTypeDomain.WORK),
                ),
            )
            val (store, interactor, handler) = setup(
                existingInitial(),
                LoadOutcome.Value(success),
            )
            assertEquals(ExerciseTypeUiModel.WEIGHTED, store.state.value.type)

            handler.invoke(Action.Common.Init)
            advanceUntilIdle()

            assertFalse(store.state.value.isLoading)
            assertEquals(ExerciseTypeUiModel.WEIGHTLESS, store.state.value.type)
            assertEquals(ExerciseTypeUiModel.WEIGHTLESS, store.state.value.initialType)
            assertEquals(1, store.state.value.draft.size)
            // initialDraft moves with draft, so the screen does not open dirty.
            assertEquals(store.state.value.draft, store.state.value.initialDraft)
            assertEquals(emptyList(), store.events)
            assertEquals(emptyList(), store.launchErrors)
            assertEquals(listOf(LoadRequest("exercise-1", null)), interactor.loadRequests)
        }
}

private sealed interface LoadOutcome {

    data class Value(val result: PlanEditorLoadResult) : LoadOutcome

    data class Failure(val cause: Throwable) : LoadOutcome
}

private data class LoadRequest(
    val exerciseUuid: String,
    val trainingUuid: String?,
)

private class CommonHandlerInteractorFake(
    private val outcome: LoadOutcome,
) : PlanEditorInteractor {

    val loadRequests = mutableListOf<LoadRequest>()

    override suspend fun loadPlan(
        exerciseUuid: String,
        trainingUuid: String?,
    ): PlanEditorLoadResult {
        loadRequests += LoadRequest(exerciseUuid, trainingUuid)
        return when (outcome) {
            is LoadOutcome.Value -> outcome.result
            is LoadOutcome.Failure -> throw outcome.cause
        }
    }

    override suspend fun savePlan(
        exerciseUuid: String,
        trainingUuid: String?,
        type: ExerciseTypeDomain,
        plan: List<PlanSetDomain>?,
    ): Nothing = error("savePlan must not be used by CommonHandler")
}

private class CommonHandlerStoreFake(
    initialState: State,
    private val testScope: TestScope,
    private val dispatcher: TestDispatcher,
) : PlanEditorHandlerStore {

    private val mutableState = MutableStateFlow(initialState)
    val events = mutableListOf<Event>()
    val launchErrors = mutableListOf<Throwable>()

    override val state: StateFlow<State> = mutableState

    override val lastAction: Action?
        get() = error("lastAction must not be read by CommonHandler")

    override val logger: Logger
        get() = error("logger must not be read by CommonHandler")

    override fun sendEvent(event: Event) {
        events += event
    }

    override fun updateState(update: (State) -> State) {
        mutableState.value = update(mutableState.value)
    }

    override fun <T> launchDefault(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        action: suspend CoroutineScope.() -> T,
    ): Job = testScope.launch(dispatcher) {
        try {
            onSuccess(action())
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            launchErrors += cause
            onError(cause)
        }
    }

    override fun consume(action: Action): Nothing =
        error("consume must not be used by CommonHandler")

    override suspend fun consumeOnMain(action: Action): Nothing =
        error("consumeOnMain must not be used by CommonHandler")

    override suspend fun updateStateImmediate(update: suspend (State) -> State): Nothing =
        error("updateStateImmediate(update) must not be used by CommonHandler")

    override suspend fun updateStateImmediate(state: State): Nothing =
        error("updateStateImmediate(state) must not be used by CommonHandler")

    override fun <T> launch(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        action: suspend CoroutineScope.() -> T,
    ): Job = error("launch must not be used by CommonHandler")

    override fun <T> Flow<T>.launch(
        onError: suspend (cause: Throwable) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        each: suspend (T) -> Unit,
    ): Job = error("Flow.launch must not be used by CommonHandler")
}
