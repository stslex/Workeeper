// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen

/**
 * `Feature` is the composition-time entry point for a feature whose Store does NOT need
 * route arguments at construction (e.g. bottom-bar destinations or single-instance
 * screens). It returns a [StoreProcessor] backed by a plain `@HiltViewModel`.
 *
 * Use [FeatureAssisted] when the screen carries a `data class Screen.<X>(...)` whose
 * fields seed the initial Store state.
 *
 * Navigation is never executed here: the Store/Handler layer dispatches navigation
 * decisions through `Navigator` (the command-bus contract), and the App/UI bridge
 * (`NavigatorExt.NavigationEventBusSetup`) executes them on the current `NavController`.
 *
 * @see [StoreProcessor]
 * @see [FeatureAssisted]
 * */
@Immutable
abstract class Feature<TProcessor : StoreProcessor<*, *, *>, TScreen : Screen> {

    @Composable
    abstract fun processor(): TProcessor

    @Suppress("UNCHECKED_CAST")
    @Composable
    inline fun <reified TSImpl : BaseStore<*, *, *>> Feature<TProcessor, TScreen>.createProcessor(): TProcessor =
        rememberStoreProcessor<TSImpl>() as TProcessor
}
