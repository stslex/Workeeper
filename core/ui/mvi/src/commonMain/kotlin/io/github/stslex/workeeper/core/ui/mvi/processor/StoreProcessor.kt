// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi.processor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import io.github.stslex.workeeper.core.core.logger.FirebaseAnalyticsHolder
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.core.logger.FirebaseEvent
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.Store.Action
import io.github.stslex.workeeper.core.ui.mvi.Store.Event
import io.github.stslex.workeeper.core.ui.mvi.Store.State
import io.github.stslex.workeeper.core.ui.mvi.performance.rememberScreenRenderRecorder
import androidx.compose.runtime.State as ComposeState

@Stable
fun interface StoreCreator<TStoreImpl : BaseStore<*, *, *>> {

    @Composable
    operator fun invoke(): TStoreImpl
}

/** Contract for consuming actions and handling events on behalf of a Store. */
@Immutable
interface StoreProcessor<S : State, A : Action, E : Event> {

    val state: ComposeState<S>

    fun consume(action: A)

    @Composable
    fun Handle(block: SuspendProcessor<E>)
}

/**
 * Remembers a [StoreProcessor] and owns the Store lifecycle wiring: `init` / `dispose`, analytics,
 * and the render trace.
 */
@Composable
inline fun <reified TStoreImpl : BaseStore<*, *, *>> rememberStoreProcessor(
    storeCreator: StoreCreator<TStoreImpl>,
): StoreProcessor<*, *, *> {
    val currentLifecycleOwner = rememberLifecycleOwner()

    val store = storeCreator()
    StoreLifecycle(store = store, lifecycleOwner = currentLifecycleOwner)

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

/** GUARD: published only for the public inline caller; the render seam stays internal API. */
@PublishedApi
@Composable
internal fun StoreLifecycle(
    store: BaseStore<*, *, *>,
    lifecycleOwner: LifecycleOwner,
) {
    val screenRenderRecorder = rememberScreenRenderRecorder()
    DisposableEffect(store, lifecycleOwner) {
        store.init(lifecycleOwner)
        FirebaseCrashlyticsHolder.setScreenName(store.name)
        FirebaseAnalyticsHolder.log(FirebaseEvent.Screen(store.name))

        screenRenderRecorder.start(store.name)
        onDispose {
            store.dispose()
            FirebaseCrashlyticsHolder.clearScreenName()
            screenRenderRecorder.stop(store.name)
        }
    }
}
