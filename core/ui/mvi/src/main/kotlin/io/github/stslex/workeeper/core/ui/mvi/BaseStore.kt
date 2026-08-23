// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Immutable
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScopeImpl
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.mvi.Store.Action
import io.github.stslex.workeeper.core.ui.mvi.Store.Event
import io.github.stslex.workeeper.core.ui.mvi.Store.State
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerCreator
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStoreEmitter
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.StoreAnalytics
import io.github.stslex.workeeper.core.ui.mvi.store.StoreConsumer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Base class for creating a store, which manages the state and events of a screen or feature.
 * It follows a unidirectional data flow pattern, where actions are consumed,
 * leading to state updates and/or events being emitted.
 *
 * @param S The type of the state held by the store.
 * @param A The type of actions that can be consumed by the store.
 * @param E The type of events that can be emitted by the store.
 * @param name A descriptive name for the store, used for logging.
 * @param initialState The initial state of the store.
 * @param handlerCreator A factory function that creates an [Handler] for a given action.
 * @param initialActions A list of actions to be consumed immediately after the store is initialized.
 * Defaults to an empty list.
 */
@Immutable
open class BaseStore<S : State, A : Action, E : Event>(
    val name: String,
    initialState: S,
    storeEmitter: HandlerStoreEmitter<S, A, E>,
    private val handlerCreator: HandlerCreator<A>,
    private val initialActions: List<A> = emptyList(),
    private val storeDispatchers: StoreDispatchers,
    val disposeActions: List<A> = emptyList(),
    val analyticsHolder: AnalyticsHolder,
    val loggerHolder: LoggerHolder,
) : ViewModel(), Store<S, A, E>, StoreConsumer<S, A, E> {

    init {
        storeEmitter.setStore(this)
    }

    private val _event: MutableSharedFlow<E> = MutableSharedFlow(
        extraBufferCapacity = EVENTS_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    override val event: SharedFlow<E> = _event.asSharedFlow()

    private val _state: MutableStateFlow<S> = MutableStateFlow(initialState)
    override val state: StateFlow<S> = _state.asStateFlow()

    private val analytics: StoreAnalytics<A, E> by lazy { analyticsHolder.create(name) }
    override val logger: Logger by lazy { loggerHolder.create(name) }

    private var _scope: AppCoroutineScope? = null
    private val scope: AppCoroutineScope
        get() = requireNotNull(_scope) {
            "Scope is not initialized for store $name. Call init() before using the store."
        }

    private var _lastAction: A? = null
    override val lastAction: A?
        get() = _lastAction

    private val allowConsumeAction: AtomicBoolean = AtomicBoolean(false)
    private val lifecycleObserver = LifecycleEventObserver { _, lifecycleEvent ->
        logger.i { "lifecycleEvent: $lifecycleEvent, for store: $name" }
        analytics.logLifecycleEvent(lifecycleEvent)
    }

    fun init(
        currentLifecycleOwner: LifecycleOwner,
        /**
         * The runtime generation's job (Phase 5 R3, spec §8.4). Every job this Store starts
         * becomes a descendant, so the generation's teardown can cancel AND JOIN them — a
         * Store's `finally` that touches the database completes before that database closes.
         */
        generationJob: Job? = null,
    ) {
        _scope = AppCoroutineScopeImpl(
            lifecycleOwner = currentLifecycleOwner,
            defaultDispatcher = storeDispatchers.defaultDispatcher,
            immediateDispatcher = storeDispatchers.mainImmediateDispatcher,
            generationJob = generationJob,
        )
        scope.addObserver(lifecycleObserver)
        allowConsumeAction.set(true)
        initialActions.forEach { consume(it) }
    }

    /**
     * Ends this Store's work. IDEMPOTENT (R3): both the composition's `onDispose` and
     * [onCleared] call it, and a generation teardown clears the ViewModelStore before the
     * composition unwinds.
     *
     * MAIN THREAD, like every other disposal path: detaching the lifecycle observer goes through
     * `LifecycleRegistry`, which enforces it. Composition disposal is already on main, and the
     * runtime's teardown clears the generation's `ViewModelStore` inside
     * `policy.mainDispatcher` for exactly this reason.
     */
    fun dispose() {
        if (_scope == null) return
        disposeActions.forEach {
            consume(it)
        }
        allowConsumeAction.set(false)
        scope.removeObserver(lifecycleObserver)
        scope.cancel()
        _scope = null
    }

    /**
     * A generation teardown clears its runtime-owned `ViewModelStore` (spec §8.4), which must
     * actually END this Store's work — before R3 `BaseStore` ignored `onCleared`, so a cleared
     * Store's jobs kept running against the outgoing generation's database.
     */
    override fun onCleared() {
        dispose()
        super.onCleared()
    }

    @Suppress("UNCHECKED_CAST")
    override fun consume(action: A) {
        if (allowConsumeAction.get().not()) {
            logger.i("consume skipped for $action")
            return
        }
        logger.i("consume: $action")
        analytics.logAction(action)
        if (lastAction != action && action !is Action.RepeatLast) {
            _lastAction = action
        }
        val handler = handlerCreator(action) as Handler<A>
        handler.invoke(action)
    }

    override suspend fun consumeOnMain(action: A) {
        withContext(storeDispatchers.mainImmediateDispatcher) {
            consume(action)
        }
    }

    /**
     * Updates the state of the screen.
     * @param update - function that updates the state
     * */
    override fun updateState(update: (S) -> S) {
        _state.update(update)
    }

    /**
     * Updates the state of the screen immediately.
     * @param update - function that updates the state
     * */
    override suspend fun updateStateImmediate(update: suspend (S) -> S) {
        _state.emit(update(state.value))
    }

    override suspend fun updateStateImmediate(state: S) {
        _state.emit(state)
    }

    /**
     * Sends an event to the screen. The event is sent on the default dispatcher.
     * @param event - event to be sent
     * */
    override fun sendEvent(event: E) {
        logger.i("sendEvent: $event")
        analytics.logEvent(event)
        sendEventWithAwait(event)
    }

    @Synchronized
    private fun sendEventWithAwait(event: E) {
        val emitted = _event.tryEmit(event)
        if (emitted.not()) {
            logger.w(
                "Event $event was try emitted: $emitted with buffer capacity ${_event.subscriptionCount.value} " +
                    "and buffer size ${_event.replayCache.size}",
            )
            scope.launch {
                _event.emit(event)
            }
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
    override fun <T> launch(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        action: suspend CoroutineScope.() -> T,
    ): Job = scope.launch(
        onError = onError,
        workDispatcher = workDispatcher,
        eachDispatcher = eachDispatcher,
        onSuccess = onSuccess,
        action = action,
    )

    /**
     * Launches a coroutine and catches exceptions. The coroutine is launched on the default dispatcher.
     * @param onError - error handler
     * @param onSuccess - success handler
     * @param action - action to be executed
     * @return Job
     * @see Job
     * */
    override fun <T> launchDefault(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        action: suspend CoroutineScope.() -> T,
    ): Job = scope.launch(
        onError = onError,
        workDispatcher = storeDispatchers.defaultDispatcher,
        eachDispatcher = storeDispatchers.defaultDispatcher,
        onSuccess = onSuccess,
        action = action,
    )

    /**
     * Launches a flow and collects it in the screenModelScope. The flow is collected on the default dispatcher.
     * @param onError - error handler
     * @param each - action for each element of the flow
     * @return Job
     * @see Flow
     * @see Job
     * */
    override fun <T> launch(
        flow: Flow<T>,
        onError: suspend (cause: Throwable) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        each: suspend (T) -> Unit,
    ): Job = scope.launch(
        flow = flow,
        workDispatcher = workDispatcher,
        eachDispatcher = eachDispatcher,
        onError = onError,
        each = each,
    )

    companion object {

        private const val EVENTS_BUFFER_CAPACITY = 32
        internal const val STORE_LOGGER_PREFIX = "SCREEN"
    }
}
