package io.github.stslex.workeeper.core.core.coroutine.scope

import io.github.stslex.workeeper.core.core.logger.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppCoroutineScope(
    val scope: CoroutineScope,
    val defaultDispatcher: CoroutineDispatcher,
    val immediateDispatcher: CoroutineDispatcher,
) {

    private fun exceptionHandler(
        eachDispatcher: CoroutineDispatcher,
        onError: suspend (cause: Throwable) -> Unit = {},
    ) = CoroutineExceptionHandler { _, throwable ->
        Log.e(throwable)
        scope.launch(eachDispatcher) {
            onError(throwable)
        }
    }

    /**
     * Launches a coroutine and catches exceptions. The coroutine is launched on the default dispatcher.
     * @param onError - error handler
     * @param onSuccess - success handler
     * @param action - action to be executed
     * @return Job
     * @see Job
     * */
    fun <T> launch(
        start: CoroutineStart = CoroutineStart.DEFAULT,
        onError: suspend (Throwable) -> Unit = {},
        onSuccess: suspend CoroutineScope.(T) -> Unit = {},
        workDispatcher: CoroutineDispatcher = defaultDispatcher,
        eachDispatcher: CoroutineDispatcher = immediateDispatcher,
        exceptionHandler: CoroutineExceptionHandler = exceptionHandler(eachDispatcher, onError),
        action: suspend CoroutineScope.() -> T,
    ): Job = scope.launch(
        start = start,
        context = workDispatcher + exceptionHandler,
        block = {
            runCatching { action() }
                .onSuccess {
                    withContext(eachDispatcher) {
                        onSuccess(it)
                    }
                }
                .onFailure {
                    withContext(eachDispatcher) {
                        onError(it)
                    }
                }
        }
    )

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
        workDispatcher: CoroutineDispatcher = defaultDispatcher,
        eachDispatcher: CoroutineDispatcher = immediateDispatcher,
        onError: suspend (cause: Throwable) -> Unit = {},
        each: suspend (T) -> Unit,
    ): Job = flow
        .catch { onError(it) }
        .onEach {
            withContext(eachDispatcher) {
                each(it)
            }
        }
        .flowOn(workDispatcher)
        .launchIn(scope)
}
