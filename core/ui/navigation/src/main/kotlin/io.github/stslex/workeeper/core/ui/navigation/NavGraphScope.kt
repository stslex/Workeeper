// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * The receiver every feature graph is declared against — the project-owned indirection that kept
 * the twelve `*Graph` call sites byte-identical across the Nav2 → Nav3 swap. Under Nav2 it
 * wrapped a `NavGraphBuilder`; it now wraps Nav3's [EntryProviderScope] plus the app-owned
 * [NavResultsSource] the result-carrying screens read through.
 *
 * [builder] and [results] are public for the same reason the Nav2 `builder` was: `core:ui:mvi`'s
 * `inline`/`reified` helpers (`navComponentScreen*`) need cross-module access. Reaching for
 * either from a feature module would mean importing `androidx.navigation3` types to name them —
 * which is what the navigation-import gate exists to catch (see its coverage note: the gate scans
 * `app/app`'s instrumented sources; feature modules are kept honest by review and by this KDoc,
 * not by a task).
 */
@Stable
class NavGraphScope(
    val builder: EntryProviderScope<NavKey>,
    val results: NavResultsSource,
)

/**
 * Register a destination for [S]. The Nav3 [entry] passes the typed key straight through — the
 * `toRoute()` decode step Nav2 needed is gone, the key IS the argument object.
 */
inline fun <reified S : Screen> NavGraphScope.navScreen(
    noinline content: @Composable (S) -> Unit,
) {
    builder.entry<S> { screen -> content(screen) }
}

/**
 * [navScreen] plus the result transport, for destinations that consume a
 * [ScreenWithResult] round trip. The only caller is `navComponentScreenWithResults`; under Nav2
 * this handed over the entry's `SavedStateHandle`, and the [NavResultsSource] it hands over now
 * carries the identical nullable contract (see the source's KDoc for the one accepted delta).
 */
inline fun <reified S : Screen> NavGraphScope.navScreenWithResults(
    noinline content: @Composable (S, NavResultsSource) -> Unit,
) {
    builder.entry<S> { screen -> content(screen, results) }
}
