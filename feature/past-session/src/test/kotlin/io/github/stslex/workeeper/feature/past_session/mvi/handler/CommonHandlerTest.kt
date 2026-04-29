// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.exercise.session.model.PerformedExerciseDetailDataModel
import io.github.stslex.workeeper.core.exercise.session.model.SessionDetailDataModel
import io.github.stslex.workeeper.feature.past_session.R
import io.github.stslex.workeeper.feature.past_session.di.PastSessionHandlerStore
import io.github.stslex.workeeper.feature.past_session.domain.PastSessionInteractor
import io.github.stslex.workeeper.feature.past_session.domain.PastSessionInteractor.DetailWithPrs
import io.github.stslex.workeeper.feature.past_session.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Event
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class CommonHandlerTest {

    private val interactor = mockk<PastSessionInteractor>(relaxed = true)
    private val resources = object : ResourceWrapper {
        override fun getString(id: Int, vararg args: Any): String = when (id) {
            R.string.feature_past_session_totals_format -> "${args[0]} · ${args[1]}"
            R.string.feature_past_session_volume_label -> "vol ${args[0]}"
            else -> error("Unexpected string id: $id")
        }

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String = when (id) {
            R.plurals.feature_past_session_exercises_count -> "$quantity exercises"
            R.plurals.feature_past_session_sets_count -> "$quantity sets"
            else -> error("Unexpected plural id: $id")
        }

        override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String =
            error("Not used in CommonHandlerTest")

        override fun formatMediumDate(timestamp: Long): String = when (timestamp) {
            60_000L -> "Apr 28"
            else -> error("Unexpected timestamp: $timestamp")
        }
    }

    @Test
    fun `Init success loads mapped detail`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(State.create(sessionUuid = SESSION_UUID), this, dispatcher)
        val handler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            store = store,
        )
        every { interactor.observeDetailWithPrs(SESSION_UUID) } returns flowOf(
            DetailWithPrs(detail = sessionDetail(), prSetUuids = emptySet()),
        )

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        val phase = store.state.value.phase
        assertTrue(phase is State.Phase.Loaded)
        assertEquals("Push Day", (phase as State.Phase.Loaded).detail.trainingName)
        assertEquals("Apr 28", phase.detail.finishedAtAbsoluteLabel)
        assertEquals("01:00", phase.detail.durationLabel)
    }

    @Test
    fun `Init with missing session switches to SessionNotFound phase`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(State.create(sessionUuid = SESSION_UUID), this, dispatcher)
        val handler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            store = store,
        )
        every { interactor.observeDetailWithPrs(SESSION_UUID) } returns flowOf(null)

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertEquals(
            State.Phase.Error(ErrorType.SessionNotFound),
            store.state.value.phase,
        )
    }

    @Test
    fun `Init failure switches to LoadFailed phase`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(State.create(sessionUuid = SESSION_UUID), this, dispatcher)
        val handler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            store = store,
        )
        every { interactor.observeDetailWithPrs(SESSION_UUID) } returns flow {
            throw IllegalStateException("boom")
        }

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertEquals(
            State.Phase.Error(ErrorType.LoadFailed),
            store.state.value.phase,
        )
    }

    @Test
    fun `Init flags PR set with isPersonalRecord`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestStore(State.create(sessionUuid = SESSION_UUID), this, dispatcher)
        val handler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            store = store,
        )
        val prSet = SetsDataModel(uuid = "set-pr", reps = 5, weight = 100.0, type = SetsDataType.WORK)
        val plainSet = SetsDataModel(uuid = "set-plain", reps = 5, weight = 80.0, type = SetsDataType.WORK)
        val performed = PerformedExerciseDetailDataModel(
            performedExerciseUuid = "performed-1",
            exerciseUuid = "exercise-1",
            exerciseName = "Bench",
            exerciseType = ExerciseTypeDataModel.WEIGHTED,
            position = 0,
            skipped = false,
            sets = listOf(plainSet, prSet),
        )
        val detail = sessionDetail().copy(exercises = listOf(performed))
        every { interactor.observeDetailWithPrs(SESSION_UUID) } returns flowOf(
            DetailWithPrs(detail = detail, prSetUuids = setOf("set-pr")),
        )

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        val phase = store.state.value.phase as State.Phase.Loaded
        val sets = phase.detail.exercises.single().sets
        assertEquals(true, sets.first { it.setUuid == "set-pr" }.isPersonalRecord)
        assertEquals(false, sets.first { it.setUuid == "set-plain" }.isPersonalRecord)
    }

    private fun sessionDetail() = SessionDetailDataModel(
        sessionUuid = SESSION_UUID,
        trainingUuid = "training-1",
        trainingName = "Push Day",
        isAdhoc = false,
        startedAt = 0L,
        finishedAt = 60_000L,
        exercises = emptyList(),
    )

    private class TestStore(
        initialState: State,
        private val testScope: TestScope,
        private val dispatcher: TestDispatcher,
    ) : PastSessionHandlerStore {

        override val state = MutableStateFlow(initialState)
        override val lastAction: Action? = null
        override val logger: Logger = mockk(relaxed = true)

        override fun sendEvent(event: Event) = Unit

        override fun consume(action: Action) = Unit

        override suspend fun consumeOnMain(action: Action) = Unit

        override fun updateState(update: (State) -> State) {
            state.value = update(state.value)
        }

        override suspend fun updateStateImmediate(update: suspend (State) -> State) {
            state.value = update(state.value)
        }

        override suspend fun updateStateImmediate(state: State) {
            this.state.value = state
        }

        override fun <T> launch(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            action: suspend CoroutineScope.() -> T,
        ): Job = testScope.launch(workDispatcher ?: dispatcher) {
            runCatching { action() }
                .onSuccess {
                    withContext(eachDispatcher ?: dispatcher) { onSuccess(it) }
                }
                .onFailure {
                    withContext(eachDispatcher ?: dispatcher) { onError(it) }
                }
        }

        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Job = this
            .catch { onError(it) }
            .onEach { withContext(eachDispatcher ?: dispatcher) { each(it) } }
            .flowOn(workDispatcher ?: dispatcher)
            .launchIn(testScope)
    }

    private companion object {

        const val SESSION_UUID = "session-1"
    }
}
