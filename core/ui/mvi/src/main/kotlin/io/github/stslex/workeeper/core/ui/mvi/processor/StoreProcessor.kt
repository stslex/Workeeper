// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi.processor

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.rememberLifecycleOwner
import io.github.stslex.workeeper.core.core.logger.FirebaseAnalyticsHolder
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.core.logger.FirebaseEvent
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.Store.Action
import io.github.stslex.workeeper.core.ui.mvi.Store.Event
import io.github.stslex.workeeper.core.ui.mvi.Store.State
import io.github.stslex.workeeper.core.ui.mvi.di.StoreGenerationDeps
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.performance.FirebaseScreenRenderRecorder
import io.github.stslex.workeeper.core.ui.navigation.Screen
import androidx.compose.runtime.State as ComposeState

@Stable
fun interface StoreCreator<TStoreImpl : BaseStore<*, *, *>> {

    @Composable
    operator fun invoke(): TStoreImpl
}

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
 * Remembers and returns a [StoreProcessor], wiring it to the store lifecycle, initializing
 * analytics/render tracing, and disposing store resources when the composable leaves the composition.
 *
 * App-Scope Collapse Step 6 (cut): the two Hilt-backed overloads (`hiltViewModel`-resolved) were removed
 * with Hilt — every feature resolves its Store through the backend-agnostic [StoreCreator] overload below
 * (via `rememberMetroStoreProcessor`, which supplies a Metro-constructed Store).
 */
@Composable
inline fun <reified TStoreImpl : BaseStore<*, *, *>> rememberStoreProcessor(
    storeCreator: StoreCreator<TStoreImpl>,
): StoreProcessor<*, *, *> {
    val currentLifecycleOwner = rememberLifecycleOwner()
    val activity = LocalActivity.current
    val context = LocalContext.current

    val store = storeCreator()
    // The CURRENT generation's lifetime (spec §8.4): every job this Store starts parents to it,
    // so a replacement's teardown cancels AND JOINS them before closing the database they touch.
    val generationJob = remember(context) {
        context.appDeps<StoreGenerationDeps>().appScopeLifetime.job
    }
    DisposableEffect(store, currentLifecycleOwner) {
        store.init(currentLifecycleOwner, generationJob)
        FirebaseCrashlyticsHolder.setScreenName(store.name)
        FirebaseAnalyticsHolder.log(FirebaseEvent.Screen(store.name))

        FirebaseScreenRenderRecorder.recordScreenTrace(
            screenName = store.name,
            activity = activity ?: context as? Activity,
        )
        onDispose {
            store.dispose()
            FirebaseCrashlyticsHolder.clearScreenName()
            FirebaseScreenRenderRecorder.stopScreenTrace(store.name)
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
