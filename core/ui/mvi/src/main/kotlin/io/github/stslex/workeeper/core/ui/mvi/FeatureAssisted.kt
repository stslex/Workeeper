// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen

/**
 * `FeatureAssisted` is the composition-time entry point for a feature whose Store needs
 * the route arguments at construction (assisted-injected `Screen` payload). It returns a
 * [StoreProcessor] for a destination that carries a route argument. The Store itself is built by
 * Metro: the arc's shape B binds the `Screen` as a `@Provides` instance on the feature's contributed
 * `@GraphExtension.Factory`, so the Store is a plain `@Inject` class and there is no assisted factory.
 *
 * Use [Feature] when the Store does not need route args. Use this class when the screen
 * carries a `data class Screen.<X>(...)` whose fields seed the initial Store state.
 *
 * Navigation is never executed here: the Store/Handler layer dispatches navigation
 * decisions through `Navigator` (the command-bus contract), and the App/UI bridge
 * (`NavigatorExt.NavigationEventBusSetup`) executes them against the Nav3 back stack.
 *
 * @see [StoreProcessor]
 * */
@Immutable
abstract class FeatureAssisted<TProcessor : StoreProcessor<*, *, *>, TScreen : Screen> {

    @Composable
    abstract fun processor(screen: TScreen): TProcessor
}
