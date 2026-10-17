// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.domain.HomeInteractor
import io.github.stslex.workeeper.feature.home.domain.model.ActiveSessionWithStatsDomain
import io.github.stslex.workeeper.feature.home.domain.model.RecentSessionDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartCardModeDomain
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import io.github.stslex.workeeper.feature.home.mvi.store.emptyPagingState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class CommonHandlerTest {

    private val interactor = CountingHomeInteractor()
    private val strings = UnreachedStringResolver()

    @Test
    fun `Init subscribes to the active session and NOT to the paged recent list`() {
        val stateFlow = MutableStateFlow(emptyPagingState())
        val store = FakeHomeHandlerStore(stateFlow)
        val handler = CommonHandler(
            interactor = interactor,
            stringResolver = strings,
            store = store,
        )

        handler.invoke(Action.Common.Init)

        assertTrue(interactor.observeActiveSessionCalls >= 1)
        // `Init` no longer subscribes to the recent list at all: it is paged, and its flow is
        // built once in `PagingHandler.pagingUiState` and collected by the screen. Asserted as an
        // absence deliberately — the old case verified a call this handler must NOT make now.
        assertEquals(0, interactor.pagedRecentCalls)
        assertEquals(emptyPagingState(), stateFlow.value)
    }

    @Test
    fun `Init builds the start card pipeline from the persisted mode`() {
        val store = FakeHomeHandlerStore(MutableStateFlow(emptyPagingState()))
        val handler = CommonHandler(
            interactor = interactor,
            stringResolver = strings,
            store = store,
        )

        handler.invoke(Action.Common.Init)

        // The persisted mode is the pipeline's head (HS6, DataStore as the single source of
        // truth); the per-mode readout hangs off it via flatMapLatest and is only reached
        // once the pipeline is collected.
        assertEquals(1, interactor.observeStartCardModeCalls)
    }

    /**
     * The handler's store as the relaxed MockK store answered it: [updateState] and the
     * update-lambda [updateStateImmediate] apply to the identity's flow, events and actions
     * record, and every [launch] / [Flow.launch] is recorded without running or collecting.
     * [logger] is the strict [SilentLogger]; members [CommonHandler] never reaches fail fast.
     */
    private class FakeHomeHandlerStore(
        private val flow: MutableStateFlow<State>,
    ) : HomeHandlerStore {

        val events = mutableListOf<Event>()
        val consumed = mutableListOf<Action>()
        val consumedOnMain = mutableListOf<Action>()
        val launches = mutableListOf<suspend CoroutineScope.() -> Unit>()
        val flowLaunches = mutableListOf<Flow<*>>()

        override val state: StateFlow<State> = flow

        override val lastAction: Nothing
            get() = error("lastAction must not be read by CommonHandler")

        override val logger: Logger = SilentLogger

        override fun sendEvent(event: Event) {
            events.add(event)
        }

        override fun consume(action: Action) {
            consumed.add(action)
        }

        override suspend fun consumeOnMain(action: Action) {
            consumedOnMain.add(action)
        }

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
        ): Job {
            launches.add { action() }
            return Job()
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
            flowLaunches.add(this)
            return Job()
        }
    }

    /**
     * `Init` logs once before it subscribes, through the lambda overload; that one is silent and
     * every other abstract member fails fast. The defaulted dedupe-key overloads are inherited.
     */
    private object SilentLogger : Logger {

        override fun i(message: () -> String) = Unit

        override fun e(throwable: Throwable, message: String?): Nothing = unreached("e")

        override fun d(message: String): Nothing = unreached("d")

        override fun d(e: Throwable, message: String): Nothing = unreached("d")

        override fun d(e: Throwable, message: () -> String): Nothing = unreached("d")

        override fun d(message: () -> String): Nothing = unreached("d")

        override fun i(message: String): Nothing = unreached("i")

        override fun v(message: String): Nothing = unreached("v")

        override fun v(message: () -> String): Nothing = unreached("v")

        override fun w(message: String): Nothing = unreached("w")

        override fun w(message: () -> String): Nothing = unreached("w")

        override fun w(message: String, throwable: Throwable): Nothing = unreached("w")

        override fun w(throwable: Throwable, message: () -> String): Nothing = unreached("w")

        private fun unreached(level: String): Nothing =
            error("CommonHandler's Init logs only through i(message: () -> String); got $level")
    }

    /**
     * Counts the three subscriptions `Init` may make and answers each with an empty flow; every
     * other member fails fast.
     */
    private class CountingHomeInteractor : HomeInteractor {

        var observeActiveSessionCalls: Int = 0
            private set
        var observeStartCardModeCalls: Int = 0
            private set
        var pagedRecentCalls: Int = 0
            private set

        override fun observeActiveSession(): Flow<ActiveSessionWithStatsDomain?> {
            observeActiveSessionCalls += 1
            return emptyFlow()
        }

        override fun observeStartCardMode(): Flow<StartCardModeDomain> {
            observeStartCardModeCalls += 1
            return emptyFlow()
        }

        override fun pagedRecent(): Flow<PagingData<RecentSessionDomain>> {
            pagedRecentCalls += 1
            return emptyFlow()
        }

        override fun observeStartCardReadout(mode: StartCardModeDomain, nowMillis: Long): Nothing =
            error("observeStartCardReadout hangs off a pipeline no identity collects")

        override suspend fun setStartCardMode(mode: StartCardModeDomain): Nothing =
            error("setStartCardMode must not be used by CommonHandler")

        override fun observeRecentTrainings(limit: Int): Nothing =
            error("observeRecentTrainings must not be used by CommonHandler")

        override suspend fun resolveStartConflict(requestedTrainingUuid: String): Nothing =
            error("resolveStartConflict must not be used by CommonHandler")

        override suspend fun getTrainingName(trainingUuid: String): Nothing =
            error("getTrainingName must not be used by CommonHandler")

        override suspend fun deleteSession(sessionUuid: String): Nothing =
            error("deleteSession must not be used by CommonHandler")
    }

    /** No CommonHandler identity reaches copy: no flow is collected, so both members fail fast. */
    private class UnreachedStringResolver : StringResolver {

        override suspend fun getString(resource: StringResource, vararg formatArgs: Any): Nothing =
            error("no CommonHandler identity reads $resource")

        override suspend fun getPluralString(
            resource: PluralStringResource,
            quantity: Int,
            vararg formatArgs: Any,
        ): Nothing = error("no CommonHandler identity reads $resource")
    }
}
