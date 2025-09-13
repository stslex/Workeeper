package io.github.stslex.workeeper.core.ui.mvi.processor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import io.github.stslex.workeeper.core.core.logger.FirebaseAnalyticsHolder
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.core.logger.FirebaseEvent
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.Store.Action
import io.github.stslex.workeeper.core.ui.mvi.Store.Event
import io.github.stslex.workeeper.core.ui.mvi.Store.State
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStoreEmitter
import io.github.stslex.workeeper.core.ui.navigation.Component
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.component.KoinScopeComponent
import org.koin.core.parameter.parametersOf
import androidx.compose.runtime.State as ComposeState

/**
 * StoreProcessor is an interface that defines the contract for processing actions and events in a store.
 * It provides methods to consume actions and handle events.
 *
 * @param S The type of the state.
 * @param A The type of the action.
 * @param E The type of the event.
 */
@Immutable
interface StoreProcessor<S : State, A : Action, E : Event> {

    val state: ComposeState<S>

    fun consume(action: A)

    @Composable
    fun Handle(block: SuspendProcessor<E>)
}

/**
 * StoreProcessorImpl is an implementation of the StoreProcessor interface.
 * It provides methods to consume actions and handle events in a store.
 *
 * @param S The type of the state.
 * @param A The type of the action.
 * @param E The type of the event.
 * @param TStoreImpl The type of the store implementation.
 */
@Composable
inline fun <
        S : State,
        A : Action,
        E : Event,
        reified TStoreImpl : BaseStore<S, A, E>,
        reified BaseHandlerStoreT : HandlerStoreEmitter<S, A, E>,
        TComponent : Component
        > KoinScopeComponent.rememberStoreProcessor(
    component: TComponent,
    key: String? = null,
): StoreProcessor<S, A, E> {
    val store = koinViewModel<TStoreImpl>(
        scope = scope,
        qualifier = scope.scopeQualifier,
        key = key,
        parameters = {
            parametersOf(component)
        }
    )
    val storeHandler = scope.get<BaseHandlerStoreT>(
        qualifier = scope.scopeQualifier,
    )
    DisposableEffect(store) {
        storeHandler.setStore(store)
        FirebaseCrashlyticsHolder.setScreenName(store.name)
        FirebaseAnalyticsHolder.log(FirebaseEvent.Screen(store.name))
        store.initialActions.forEach { store.consume(it) }
        onDispose {
            storeHandler.clearStore()
            FirebaseCrashlyticsHolder.clearScreenName()
            store.disposeActions.forEach { store.consume(it) }
        }
    }
    val actionProcessor = remember { ActionProcessor(store) }
    val effectsProcessor = remember { EffectsProcessor(store) }
    val state = remember { store.state }.collectAsState()
    return remember {
        StoreProcessorImpl(
            actionProcessor = actionProcessor,
            eventProcessor = effectsProcessor,
            state = state,
        )
    }
}
