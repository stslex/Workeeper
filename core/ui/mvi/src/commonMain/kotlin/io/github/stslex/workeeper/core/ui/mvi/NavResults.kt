// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.stslex.workeeper.core.ui.navigation.NavResultKey
import io.github.stslex.workeeper.core.ui.navigation.NavResultsSource
import io.github.stslex.workeeper.core.ui.navigation.ScreenWithResult
import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KClass

/**
 * The read side of [ScreenWithResult]: what a destination gets back from one it opened. A graph
 * forwards a result to the Store ([OnResult] → `processor.consume(...)`), never branches on it.
 */
@Stable
class NavResults @PublishedApi internal constructor(
    private val source: NavResultsSource,
) {

    /** The live result from [destination], or `null` when there is none. */
    fun <S, R : Any> result(
        destination: KClass<S>,
    ): StateFlow<R?> where S : ScreenWithResult<R> {
        @Suppress("UNCHECKED_CAST")
        return source.result(NavResultKey.of(destination)) as StateFlow<R?>
    }

    /**
     * Drop the result from [destination] so re-entry does not re-deliver it. Prefer [OnResult],
     * which clears as part of delivery.
     */
    fun <S, R : Any> clear(
        destination: KClass<S>,
    ) where S : ScreenWithResult<R> {
        source.clearResult(NavResultKey.of(destination))
    }

    /**
     * Run [onResult] once per result produced by [destination], then clear it. `null` is never
     * delivered, so the callback does not fire on entry the way a raw flow collection would.
     */
    @Composable
    fun <S, R : Any> OnResult(
        destination: KClass<S>,
        onResult: (R) -> Unit,
    ) where S : ScreenWithResult<R> {
        val value by result(destination).collectAsState()
        LaunchedEffect(value) {
            val result = value ?: return@LaunchedEffect
            onResult(result)
            clear<S, R>(destination)
        }
    }
}
