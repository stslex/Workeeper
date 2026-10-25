package io.github.stslex.workeeper.core.core.coroutine.scope

import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.github.stslex.workeeper.core.core.logger.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class AppCoroutineScopeImpl(
    private val lifecycleOwner: LifecycleOwner,
    override val defaultDispatcher: CoroutineDispatcher,
    override val immediateDispatcher: CoroutineDispatcher,
    /** Records every launch failure; see [AppCoroutineScope.launch]. */
    private val logger: Logger,
    /** GUARD: required generation parent; an autonomous Store scope could outlive its database. */
    generationJob: Job,
) : AppCoroutineScope, CoroutineScope {

    // Right-side Job wins CoroutineContext.plus key conflicts, retaining the generation parent.
    override val coroutineContext: CoroutineContext =
        lifecycleOwner.lifecycleScope.coroutineContext +
            CoroutineName("FeatureScope") +
            SupervisorJob(generationJob)

    private fun exceptionHandler(
        eachDispatcher: CoroutineDispatcher?,
        onError: suspend (cause: Throwable) -> Unit = {},
    ) = CoroutineExceptionHandler { _, throwable ->
        logger.e(throwable)
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
                    .onFailure { failure ->
                        // GUARD (D4): a cancelled launch is not a failure. The check reads this block's own scope,
                        // not the Store's, and rethrows before the record, as withContext would before onError.
                        ensureActive()
                        logger.e(failure)
                        withContext(eachDispatcher) { onError(failure) }
                    }
            },
        )
    }

    override fun <T> launch(
        flow: Flow<T>,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        onError: suspend (cause: Throwable) -> Unit,
        each: suspend (T) -> Unit,
    ): Job = flow
        // Flow.catch rethrows the collector's own cancellation, so only a failure arrives (D4).
        .catch { cause ->
            logger.e(cause)
            onError(cause)
        }
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
