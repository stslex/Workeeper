// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * The receiver every feature graph is declared against: Nav3's [EntryProviderScope] plus the
 * app-owned [NavResultsSource]. Feature modules must not name `androidx.navigation3` types.
 */
@Stable
class NavGraphScope(
    val builder: EntryProviderScope<NavKey>,
    val results: NavResultsSource,
)

/** Register a destination for [S]; under Nav3 the key IS the argument object. */
inline fun <reified S : Screen> NavGraphScope.navScreen(
    noinline content: @Composable (S) -> Unit,
) {
    builder.entry<S> { screen -> content(screen) }
}

/** [navScreen] plus the result transport, for destinations that consume a [ScreenWithResult]. */
inline fun <reified S : Screen> NavGraphScope.navScreenWithResults(
    noinline content: @Composable (S, NavResultsSource) -> Unit,
) {
    builder.entry<S> { screen -> content(screen, results) }
}
