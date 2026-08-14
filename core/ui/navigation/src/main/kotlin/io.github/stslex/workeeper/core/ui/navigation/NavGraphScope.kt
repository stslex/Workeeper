// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute

/**
 * The receiver every feature graph registers against.
 *
 * It exists so that no feature names the navigation library's builder. Today [builder] is
 * Nav2's [NavGraphBuilder]; under Nav3 the same registration is written against
 * `EntryProviderBuilder`. Feature graphs are declared as `fun NavGraphScope.<name>Graph(…)`
 * and call [navScreen] / `navComponentScreen*`, none of which mention either type — so when
 * the swap lands, this class and the two primitives below are re-pointed and **no call site
 * moves.** That is the whole reason it is here: 1.3's diff should be the implementation,
 * not twelve graphs.
 *
 * [builder] is public because `core:ui:mvi`'s registration helpers are `inline` (they need
 * `reified` screen types) and must reach it from another module. Reaching for it from a
 * feature would mean importing [NavGraphBuilder] to name its type, which is exactly what
 * the navigation-import gate is there to catch.
 */
@JvmInline
value class NavGraphScope(val builder: NavGraphBuilder)

/**
 * Register [S] as a destination.
 *
 * The route argument is decoded from the back stack entry and handed to [content], so a
 * screen never parses its own arguments.
 */
inline fun <reified S : Screen> NavGraphScope.navScreen(
    noinline content: @Composable AnimatedContentScope.(S) -> Unit,
) {
    builder.composable<S> { backStackEntry ->
        content(backStackEntry.toRoute())
    }
}

/**
 * [navScreen] plus the entry's [SavedStateHandle].
 *
 * Not for feature use: the only caller is `navComponentScreenWithResults`, which wraps the
 * handle in a `NavResults` before anything sees it. A graph that took the raw handle would
 * be back to string keys and erased values — the shape [ScreenWithResult] replaced.
 */
inline fun <reified S : Screen> NavGraphScope.navScreenWithState(
    noinline content: @Composable AnimatedContentScope.(S, SavedStateHandle) -> Unit,
) {
    builder.composable<S> { backStackEntry ->
        content(backStackEntry.toRoute(), backStackEntry.savedStateHandle)
    }
}
