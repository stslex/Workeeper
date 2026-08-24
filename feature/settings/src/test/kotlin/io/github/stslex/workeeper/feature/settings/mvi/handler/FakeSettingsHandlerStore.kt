// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStore
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

/**
 * Test double for `SettingsHandlerStore`, running closures synchronously on the supplied
 * dispatcher. Hand-written because MockK cannot cleanly mock the `Flow<T>.launch` extension.
 */
internal class FakeSettingsHandlerStore(
    private val dispatcher: CoroutineDispatcher,
) : SettingsHandlerStore {

    val stateFlow: MutableStateFlow<State> =
        MutableStateFlow(State.initial(appVersion = "1.0.0", appVersionCode = 1))
    val events: MutableList<Event> = mutableListOf()
    val consumedActions: MutableList<Action> = mutableListOf()

    private val eventBus = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    private val collectorScope = CoroutineScope(Job() + dispatcher)

    fun dispose() {
        collectorScope.coroutineContext[Job]?.cancel()
    }

    override val state: StateFlow<State> = stateFlow
    override val lastAction: Action? = null
    override val logger: Logger = mockk(relaxed = true)

    override fun sendEvent(event: Event) {
        events += event
        eventBus.tryEmit(event)
    }

    override fun consume(action: Action) {
        consumedActions += action
    }

    override suspend fun consumeOnMain(action: Action) {
        consumedActions += action
    }

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
    ): Job = runBlocking(dispatcher) {
        val job = Job()
        runCatching { action() }
            .onSuccess { onSuccess(it) }
            .onFailure { onError(it) }
        job.apply { complete() }
    }

    override fun <T> launchDefault(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        action: suspend CoroutineScope.() -> T,
    ): Job = runBlocking(dispatcher) {
        val job = Job()
        runCatching { action() }
            .onSuccess { onSuccess(it) }
            .onFailure { onError(it) }
        job.apply { complete() }
    }

    override fun <T> Flow<T>.launch(
        onError: suspend (cause: Throwable) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        each: suspend (T) -> Unit,
    ): Job = onEach { each(it) }.launchIn(collectorScope)
}
