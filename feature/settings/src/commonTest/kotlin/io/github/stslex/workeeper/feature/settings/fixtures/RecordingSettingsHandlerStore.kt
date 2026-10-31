// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.fixtures

import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStore
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class RecordingSettingsHandlerStore(
    private val stateFlow: MutableStateFlow<State> = MutableStateFlow(
        State.initial(appVersion = "1.0.0", appVersionCode = 1, backupAvailable = true),
    ),
) : SettingsHandlerStore {
    val events = mutableListOf<Event>()
    val consumedActions = mutableListOf<Action>()
    override val state: StateFlow<State> = stateFlow
    override val lastAction: Action? = null
    override val logger: Logger = Log.tag("SettingsTest")
    override fun sendEvent(event: Event) { events += event }
    override fun consume(action: Action) { consumedActions += action }
    override suspend fun consumeOnMain(action: Action) { consumedActions += action }
    override fun updateState(update: (State) -> State) { stateFlow.value = update(stateFlow.value) }
    override suspend fun updateStateImmediate(update: suspend (State) -> State) {
        stateFlow.value = update(stateFlow.value)
    }
    override suspend fun updateStateImmediate(state: State) { stateFlow.value = state }
    override fun <T> launch(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        action: suspend CoroutineScope.() -> T,
    ): Job = CoroutineScope(Dispatchers.Unconfined).launch {
        try { onSuccess(action()) } catch (error: Throwable) { onError(error) }
    }
    override fun <T> launchDefault(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        action: suspend CoroutineScope.() -> T,
    ): Job = launch(onError, onSuccess, null, null, action)
    override fun <T> Flow<T>.launch(
        onError: suspend (Throwable) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        each: suspend (T) -> Unit,
    ): Job = error("Unexpected observer in handler fixture")
}
