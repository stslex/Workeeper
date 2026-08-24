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

    /** Collects [flow] on this scope: work on [workDispatcher], [each] on [eachDispatcher]. */
    fun <T> launch(
        flow: Flow<T>,
        workDispatcher: CoroutineDispatcher? = null,
        eachDispatcher: CoroutineDispatcher? = null,
        onError: suspend (cause: Throwable) -> Unit = {},
        each: suspend (T) -> Unit,
    ): Job

    /** Launches [action] on this scope, routing its failures to [onError]. */
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
