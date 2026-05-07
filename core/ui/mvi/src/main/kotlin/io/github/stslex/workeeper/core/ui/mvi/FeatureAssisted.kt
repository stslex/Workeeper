// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreFactory
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen

/**
 * `FeatureAssisted` is the composition-time entry point for a feature whose Store needs
 * the route arguments at construction (assisted-injected `Screen` payload). It returns a
 * [StoreProcessor] backed by a Hilt assisted-factory `StoreFactory<TScreen, TStoreImpl>`.
 *
 * Use [Feature] when the Store does not need route args. Use this class when the screen
 * carries a `data class Screen.<X>(...)` whose fields seed the initial Store state.
 *
 * Navigation is never executed here: the Store/Handler layer dispatches navigation
 * decisions through `Navigator` (the command-bus contract), and the App/UI bridge
 * (`NavigatorExt.NavigationEventBusSetup`) executes them on the current `NavController`.
 *
 * @see [StoreProcessor]
 * @see [io.github.stslex.workeeper.core.ui.mvi.processor.StoreFactory]
 * */
@Immutable
abstract class FeatureAssisted<TProcessor : StoreProcessor<*, *, *>, TScreen : Screen> {

    @Composable
    abstract fun processor(screen: TScreen): TProcessor

    @Suppress("UNCHECKED_CAST")
    @Composable
    inline fun <
        reified TSImpl : BaseStore<*, *, *>,
        reified TFactory : StoreFactory<TScreen, TSImpl>,
        > FeatureAssisted<TProcessor, TScreen>.createProcessor(
        screen: TScreen,
    ): TProcessor = rememberStoreProcessor<TSImpl, TScreen, TFactory>(screen) as TProcessor
}
