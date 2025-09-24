package io.github.stslex.workeeper.core.ui.mvi.store

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.mvi.Store.Event
import io.github.stslex.workeeper.core.ui.mvi.Store.State
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface StoreConsumer<S : State, A : Store.Action, in E : Event> {

    val state: StateFlow<S>

    val lastAction: A?

    val logger: Logger

    val scope: AppCoroutineScope

    fun sendEvent(event: E)

    fun consume(action: A)

    fun updateState(update: (S) -> S)

    suspend fun updateStateImmediate(update: suspend (S) -> S)

    suspend fun updateStateImmediate(state: S)

    /**
     * Launches a coroutine and catches exceptions. The coroutine is launched on the default dispatcher..
     * @param onError - error handler
     * @param onSuccess - success handler
     * @param action - action to be executed
     * @return Job
     * @see Job
     * */
    fun <T> launch(
        onError: suspend (Throwable) -> Unit = {},
        onSuccess: suspend CoroutineScope.(T) -> Unit = {},
        workDispatcher: CoroutineDispatcher = scope.defaultDispatcher,
        eachDispatcher: CoroutineDispatcher = scope.defaultDispatcher,
        action: suspend CoroutineScope.() -> T,
    ): Job

    /**
     * Launches a flow and collects it in the screenModelScope. The flow is collected on the default dispatcher.
     * @param onError - error handler
     * @param each - action for each element of the flow
     * @return Job
     * @see Flow
     * @see Job
     * */
    fun <T> launch(
        flow: Flow<T>,
        onError: suspend (cause: Throwable) -> Unit = {},
        workDispatcher: CoroutineDispatcher = scope.defaultDispatcher,
        eachDispatcher: CoroutineDispatcher = scope.defaultDispatcher,
        each: suspend (T) -> Unit,
    ): Job
}
