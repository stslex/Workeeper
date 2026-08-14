// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

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
 *
 * **The constructor is a wider seam than the import gate measures, and it is transitional.**
 * The gate counts `androidx.navigation` imports; it cannot see that anything holding a
 * `NavGraphBuilder` — `NavHost`'s own content lambda, say — can wrap one without naming the
 * type. Two instrumented tests do exactly that (`ExerciseCreatePersistenceTest`,
 * `AllTrainingsExtensionDbVisibilityTest`), mounting their own `NavHost` as DI/persistence
 * scaffolding and calling `NavGraphScope(this)`.
 *
 * That is scaffolding, not architecture, and it does not survive: when the wrapped type
 * changes, every such call site breaks loudly rather than silently. Both files are filed for
 * rewrite in `documentation/tech-debt.md`, alongside the androidTest navigation-import gate
 * they are the two named exclusions from. Nothing in production constructs this except
 * `AppNavigationHost`, which is the one place that is supposed to.
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
    noinline content: @Composable (S) -> Unit,
) {
    builder.composable<S> { backStackEntry ->
        content(backStackEntry.toRoute())
    }
}

/**
 * [navScreen] plus the entry's [SavedStateHandle].
 *
 * Not for feature use: the only caller is `navComponentScreenWithResults`, which wraps the
 * handle in a `NavResults` before anything sees it. A graph holding the raw handle reads
 * results by string key at an erased type, which is what [ScreenWithResult] exists to
 * prevent.
 */
inline fun <reified S : Screen> NavGraphScope.navScreenWithState(
    noinline content: @Composable (S, SavedStateHandle) -> Unit,
) {
    builder.composable<S> { backStackEntry ->
        content(backStackEntry.toRoute(), backStackEntry.savedStateHandle)
    }
}
