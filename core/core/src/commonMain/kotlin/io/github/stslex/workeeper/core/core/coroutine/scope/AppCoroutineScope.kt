package io.github.stslex.workeeper.core.core.coroutine.scope

import androidx.lifecycle.LifecycleObserver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

interface AppCoroutineScope : CoroutineScope {

    val defaultDispatcher: CoroutineDispatcher

    val immediateDispatcher: CoroutineDispatcher

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
        workDispatcher: CoroutineDispatcher? = null,
        eachDispatcher: CoroutineDispatcher? = null,
        onError: suspend (cause: Throwable) -> Unit = {},
        each: suspend (T) -> Unit,
    ): Job

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
        workDispatcher: CoroutineDispatcher? = null,
        eachDispatcher: CoroutineDispatcher? = null,
        exceptionHandler: CoroutineExceptionHandler? = null,
        action: suspend CoroutineScope.() -> T,
    ): Job

    fun addObserver(observer: LifecycleObserver)

    fun removeObserver(observer: LifecycleObserver)
}
