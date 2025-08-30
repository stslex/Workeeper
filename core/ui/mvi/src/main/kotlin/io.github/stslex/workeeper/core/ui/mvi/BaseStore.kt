package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.mvi.Store.Action
import io.github.stslex.workeeper.core.ui.mvi.Store.Event
import io.github.stslex.workeeper.core.ui.mvi.Store.State
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerCreator
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStore
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

/**
 * Base class for creating a store, which manages the state and events of a screen or feature.
 * It follows a unidirectional data flow pattern, where actions are consumed, leading to state updates and/or events being emitted.
 *
 * @param S The type of the state held by the store.
 * @param A The type of actions that can be consumed by the store.
 * @param E The type of events that can be emitted by the store.
 * @param HStore The type of the handler store, which provides context to action handlers. Must inherit from [io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStore].
 * @param name A descriptive name for the store, used for logging.
 * @param initialState The initial state of the store.
 * @param handlerCreator A factory function that creates an [Handler] for a given action.
 * @param initialActions A list of actions to be consumed immediately after the store is initialized. Defaults to an empty list.
 */
@Immutable
open class BaseStore<S : State, A : Action, E : Event, HStore : HandlerStore<S, A, E>>(
    name: String,
    initialState: S,
    override val appDispatcher: AppDispatcher,
    private val handlerCreator: HandlerCreator<S, A, E, HStore>,
    val initialActions: List<A> = emptyList(),
    val disposeActions: List<A> = emptyList()
) : ViewModel(), Store<S, A, E>, HandlerStore<S, A, E> {

    private val _event: MutableSharedFlow<E> = MutableSharedFlow()
    override val event: SharedFlow<E> = _event.asSharedFlow()

    private val _state: MutableStateFlow<S> = MutableStateFlow(initialState)
    override val state: StateFlow<S> = _state.asStateFlow()

    override val scope: AppCoroutineScope = AppCoroutineScope(viewModelScope, appDispatcher)
    override val logger = Log.tag(name)

    private var _lastAction: A? = null
    override val lastAction: A?
        get() = _lastAction

    @Suppress("UNCHECKED_CAST")
    override fun consume(action: A) {
        logger.i("consume: $action")
        if (lastAction != action && action !is Action.RepeatLast) {
            _lastAction = action
        }
        val handler = handlerCreator(action) as Handler<A, HStore>
        handler.invoke(this as HStore, action)
    }

    /**
     * Updates the state of the screen.
     * @param update - function that updates the state
     * */
    override fun updateState(update: (S) -> S) {
        _state.update(update)
    }

    /**
     * Sends an event to the screen. The event is sent on the default dispatcher of the AppDispatcher.
     * @param event - event to be sent
     * @see AppDispatcher
     * */
    override fun sendEvent(event: E) {
        logger.i("sendEvent: $event")
        _event.tryEmit(event).also { isProcess ->
            if (isProcess.not()) {
                scope.launch { _event.emit(event) }
            }
        }
    }

    /**
     * Launches a coroutine and catches exceptions. The coroutine is launched on the default dispatcher of the AppDispatcher.
     * @param onError - error handler
     * @param onSuccess - success handler
     * @param action - action to be executed
     * @return Job
     * @see Job
     * @see AppDispatcher
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
        action = action
    )

    /**
     * Launches a flow and collects it in the screenModelScope. The flow is collected on the default dispatcher. of the AppDispatcher.
     * @param onError - error handler
     * @param each - action for each element of the flow
     * @return Job
     * @see Flow
     * @see Job
     * @see AppDispatcher
     * */
    override fun <T> Flow<T>.launch(
        onError: suspend (cause: Throwable) -> Unit,
        workDispatcher: CoroutineDispatcher,
        eachDispatcher: CoroutineDispatcher,
        each: suspend (T) -> Unit
    ): Job = scope.launch(
        flow = this,
        workDispatcher = workDispatcher,
        eachDispatcher = eachDispatcher,
        onError = onError,
        each = each,
    )

}