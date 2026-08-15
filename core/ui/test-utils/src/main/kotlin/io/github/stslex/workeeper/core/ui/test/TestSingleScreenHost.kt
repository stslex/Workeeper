// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.test

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.core.ui.navigation.NavResultsSource
import io.github.stslex.workeeper.core.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A real Nav3 host around a single feature graph, for instrumented scaffolding tests.
 *
 * Exists so that the two `:app:app` tests which mount a graph as DI/persistence scaffolding
 * (`ExerciseCreatePersistenceTest`, `AllTrainingsExtensionDbVisibilityTest`) can do it WITHOUT
 * importing `androidx.navigation3` — the androidTest navigation-import gate bans the library
 * wholesale and, since the Nav3 swap, carries no per-file exclusions. Under Nav2 those tests
 * mounted their own `NavHost` and were the gate's two named exceptions; this helper is what
 * deleted them.
 *
 * The decorator pair matches production (`AppNavigationHost`): per-entry saveable state plus
 * per-entry `ViewModelStore`, so a Store resolved through `rememberMetroStoreProcessor` scopes
 * exactly as it does in the app. The stack is a plain in-memory list — scaffolding needs no
 * process-death persistence.
 */
@Composable
fun TestSingleScreenHost(
    start: Screen,
    results: NavResultsSource = NoopNavResultsSource,
    graph: NavGraphScope.() -> Unit,
) {
    val backStack = remember { mutableStateListOf<NavKey>(start) }
    NavDisplay(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            NavGraphScope(this, results).graph()
        },
    )
}

/** No-delivery source for scaffolding that never consumes a result. */
object NoopNavResultsSource : NavResultsSource {

    private val empty = MutableStateFlow<Any?>(null)

    override fun result(key: String): StateFlow<Any?> = empty

    override fun setResult(key: String, result: Any) = Unit

    override fun clearResult(key: String) = Unit
}
