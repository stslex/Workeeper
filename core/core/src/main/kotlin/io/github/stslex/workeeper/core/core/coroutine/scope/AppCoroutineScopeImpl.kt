package io.github.stslex.workeeper.core.core.coroutine.scope

import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
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

class AppCoroutineScopeImpl(
    private val lifecycleOwner: LifecycleOwner,
    override val defaultDispatcher: CoroutineDispatcher,
    override val immediateDispatcher: CoroutineDispatcher,
) : AppCoroutineScope, CoroutineScope by lifecycleOwner.lifecycleScope {

    private fun exceptionHandler(
        eachDispatcher: CoroutineDispatcher?,
        onError: suspend (cause: Throwable) -> Unit = {},
    ) = CoroutineExceptionHandler { _, throwable ->
        Log.e(throwable)
        launch(eachDispatcher ?: defaultDispatcher) {
            onError(throwable)
        }
    }

    override fun <T> launch(
        start: CoroutineStart,
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        exceptionHandler: CoroutineExceptionHandler?,
        action: suspend CoroutineScope.() -> T,
    ): Job {
        val eachDispatcher = eachDispatcher ?: immediateDispatcher
        val workDispatcher = workDispatcher ?: defaultDispatcher
        val exceptionHandler = exceptionHandler ?: exceptionHandler(workDispatcher, onError)
        return launch(
            start = start,
            context = workDispatcher + exceptionHandler,
            block = {
                runCatching { action() }
                    .onSuccess {
                        withContext(eachDispatcher) { onSuccess(it) }
                    }
                    .onFailure {
                        withContext(eachDispatcher) { onError(it) }
                    }
            },
        )
    }

    /**
     * Launches a flow and collects it in the screenModelScope. The flow is collected on the default dispatcher.
     * @param onError - error handler
     * @param each - action for each element of the flow
     * @return Job
     * @see kotlinx.coroutines.flow.Flow
     * @see Job
     * */
    override fun <T> launch(
        flow: Flow<T>,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        onError: suspend (cause: Throwable) -> Unit,
        each: suspend (T) -> Unit,
    ): Job = flow
        .catch { onError(it) }
        .onEach {
            withContext(eachDispatcher ?: immediateDispatcher) { each(it) }
        }
        .flowOn(workDispatcher ?: defaultDispatcher)
        .launchIn(this)

    override fun addObserver(observer: LifecycleObserver) {
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    override fun removeObserver(observer: LifecycleObserver) {
        lifecycleOwner.lifecycle.removeObserver(observer)
    }
}
