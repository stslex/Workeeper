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
 * A real Nav3 host around a single feature graph, so scaffolding tests can mount one without
 * importing `androidx.navigation3` past the androidTest import gate. See documentation/testing.md.
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
