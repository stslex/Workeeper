package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.mvi.Store.Action
import io.github.stslex.workeeper.core.ui.mvi.Store.Event
import io.github.stslex.workeeper.core.ui.mvi.Store.State
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerCreator
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStoreEmitter
import io.github.stslex.workeeper.core.ui.mvi.store.StoreConsumer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Base class for creating a store, which manages the state and events of a screen or feature.
 * It follows a unidirectional data flow pattern, where actions are consumed, leading to state updates and/or events being emitted.
 *
 * @param S The type of the state held by the store.
 * @param A The type of actions that can be consumed by the store.
 * @param E The type of events that can be emitted by the store.
 * @param name A descriptive name for the store, used for logging.
 * @param initialState The initial state of the store.
 * @param handlerCreator A factory function that creates an [Handler] for a given action.
 * @param initialActions A list of actions to be consumed immediately after the store is initialized. Defaults to an empty list.
 */
@Immutable
open class BaseStore<S : State, A : Action, E : Event>(
    val name: String,
    initialState: S,
    private val storeEmitter: HandlerStoreEmitter<S, A, E>,
    private val handlerCreator: HandlerCreator<A>,
    private val initialActions: List<A> = emptyList(),
    storeDispatchers: StoreDispatchers,
    val disposeActions: List<A> = emptyList(),
    val analytics: StoreAnalytics<A, E> = AnalyticsHolder.createStore(name),
    override val logger: Logger = storeLogger(name),
) : ViewModel(), Store<S, A, E>, StoreConsumer<S, A, E> {

    private val _event: MutableSharedFlow<E> = MutableSharedFlow()
    override val event: SharedFlow<E> = _event.asSharedFlow()

    private val _state: MutableStateFlow<S> = MutableStateFlow(initialState)
    override val state: StateFlow<S> = _state.asStateFlow()

    override val scope: AppCoroutineScope = AppCoroutineScope(
        scope = viewModelScope,
        defaultDispatcher = storeDispatchers.defaultDispatcher,
        immediateDispatcher = storeDispatchers.mainImmediateDispatcher,
    )

    private var _lastAction: A? = null
    override val lastAction: A?
        get() = _lastAction

    private val allowConsumeAction: AtomicBoolean = AtomicBoolean(false)

    fun init() {
        allowConsumeAction.set(true)
        initialActions.forEach { consume(it) }
    }

    fun initEmitter() {
        /*todo: check why emitter sometimes doesn't have store instance
         *  seems that emitter recreate instance
         *   it could be problems in StoreProcessor lifecycle creation */
        storeEmitter.setStore(this)
    }

    fun dispose() {
        disposeActions.forEach { consume(it) }
        allowConsumeAction.set(false)
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
        _event.tryEmit(event).also { isProcess ->
            if (isProcess.not()) {
                scope.launch { _event.emit(event) }
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
        workDispatcher: CoroutineDispatcher,
        eachDispatcher: CoroutineDispatcher,
        action: suspend CoroutineScope.() -> T,
    ) = scope.launch(
        onError = onError,
        workDispatcher = workDispatcher,
        eachDispatcher = eachDispatcher,
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
        workDispatcher: CoroutineDispatcher,
        eachDispatcher: CoroutineDispatcher,
        each: suspend (T) -> Unit,
    ): Job = scope.launch(
        flow = flow,
        workDispatcher = workDispatcher,
        eachDispatcher = eachDispatcher,
        onError = onError,
        each = each,
    )

    companion object {

        internal const val STORE_LOGGER_PREFIX = "MVI_STORE"

        fun storeLogger(name: String) = Log.tag("${STORE_LOGGER_PREFIX}_$name")
    }
}
