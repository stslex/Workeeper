package io.github.stslex.workeeper.core.ui.mvi.processor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.stslex.workeeper.core.core.logger.FirebaseAnalyticsHolder
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.core.logger.FirebaseEvent
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.Store.Action
import io.github.stslex.workeeper.core.ui.mvi.Store.Event
import io.github.stslex.workeeper.core.ui.mvi.Store.State
import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.LocalRootComponent
import io.github.stslex.workeeper.core.ui.navigation.Screen
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
 * @param component The component associated with the store.
 * @param key An optional key for the store.
 */
@Composable
inline fun <
    reified TStoreImpl : BaseStore<*, *, *>,
    TComponent : Component<*>,
    reified TFactory : StoreFactory<TComponent, TStoreImpl>,
    > rememberStoreProcessor(
    screen: Screen,
    key: String? = null,
): StoreProcessor<*, *, *> {
    val rootComponent = LocalRootComponent.current

    val component = remember(screen) {
        rootComponent.createComponent(screen) as TComponent
    }

    val store = hiltViewModel<TStoreImpl, TFactory>(key = key) {
        it.create(component)
    }.apply { initEmitter() }

    DisposableEffect(store) {
        store.initEmitter()
        store.init()
        FirebaseCrashlyticsHolder.setScreenName(store.name)
        FirebaseAnalyticsHolder.log(FirebaseEvent.Screen(store.name))
        onDispose {
            store.dispose()
            FirebaseCrashlyticsHolder.clearScreenName()
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
