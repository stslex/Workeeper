// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.handler

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.past_session.R
import io.github.stslex.workeeper.feature.past_session.di.PastSessionHandlerStore
import io.github.stslex.workeeper.feature.past_session.domain.PastSessionInteractor
import io.github.stslex.workeeper.feature.past_session.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Event
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class CommonHandlerTest {

    private val interactor = mockk<PastSessionInteractor>(relaxed = true)
    private val resources = object : ResourceWrapper {
        override fun getString(id: Int, vararg args: Any): String = when (id) {
            R.string.feature_past_session_totals_format -> "${args[0]} · ${args[1]}"
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
        coEvery { interactor.getSessionDetail(SESSION_UUID) } returns sessionDetail()

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
        coEvery { interactor.getSessionDetail(SESSION_UUID) } returns null

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
        coEvery { interactor.getSessionDetail(SESSION_UUID) } throws IllegalStateException("boom")

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertEquals(
            State.Phase.Error(ErrorType.LoadFailed),
            store.state.value.phase,
        )
    }

    private fun sessionDetail() = io.github.stslex.workeeper.core.exercise.session.model.SessionDetailDataModel(
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
        testScope: TestScope,
        dispatcher: TestDispatcher,
    ) : PastSessionHandlerStore {

        override val state = MutableStateFlow(initialState)
        override val lastAction: Action? = null
        override val logger: Logger = mockk(relaxed = true)
        override val scope = AppCoroutineScope(
            scope = testScope,
            defaultDispatcher = dispatcher,
            immediateDispatcher = dispatcher,
        )

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
    }

    private companion object {

        const val SESSION_UUID = "session-1"
    }
}
