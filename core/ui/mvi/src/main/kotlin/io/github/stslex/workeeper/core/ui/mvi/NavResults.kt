// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.SavedStateHandle
import io.github.stslex.workeeper.core.ui.navigation.NavResultKey
import io.github.stslex.workeeper.core.ui.navigation.ScreenWithResult
import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KClass

/**
 * The read side of [ScreenWithResult] — what a destination gets back from a destination it
 * opened.
 *
 * Handed to a graph's content lambda by `navComponentScreenWithResults`. It exists so that
 * no feature names the transport: there is no string key to get wrong, no `Any?` to cast,
 * and no [SavedStateHandle] in a feature's imports. The type comes off the destination.
 *
 * **What a graph is allowed to do with a result: forward it.** Reading a result is state,
 * and state belongs in the Store — so the shape at every call site is
 * [OnResult] → `processor.consume(Action…)`, with the parsing and the decision on the far
 * side of that call. A graph that branches on a result has taken on the Store's job.
 *
 * The handle is deliberately private and not exposed: the point of this type is that it is
 * the *only* thing the graph gets, so the transport cannot leak back out through it.
 */
@Stable
class NavResults @PublishedApi internal constructor(
    private val handle: SavedStateHandle,
) {

    /**
     * The live result from [destination], or `null` for "no result" — never opened, or
     * opened and dismissed without producing one. See [ScreenWithResult] for why absence is
     * `null` rather than a `Cancelled` case.
     */
    fun <S, R : Any> result(
        destination: KClass<S>,
    ): StateFlow<R?> where S : ScreenWithResult<R> =
        handle.getStateFlow(NavResultKey.of(destination), null)

    /**
     * Drop the result from [destination] so re-entry does not re-deliver it.
     *
     * Callers should prefer [OnResult], which does this as part of delivery. A result left
     * in place re-fires on the next resume — the bug this clear exists to prevent.
     */
    fun <S, R : Any> clear(
        destination: KClass<S>,
    ) where S : ScreenWithResult<R> {
        handle[NavResultKey.of(destination)] = null
    }

    /**
     * Run [onResult] once per result produced by [destination], then clear it.
     *
     * Fires only on a real result: `null` — the state on arrival, and again after the
     * clear — is not delivered, so the callback does not run once on entry the way a raw
     * flow collection would.
     *
     * ```
     * results.OnResult(Screen.PlanEditor::class) { saved ->
     *     if (saved) processor.consume(Action.Common.Reload)
     * }
     * ```
     *
     * **Note for anyone writing a test against this.** `AppCoroutineScopeImpl.launch(flow, …)`
     * applies `.catch { onError(it) }`, so a flow error inside a Store is swallowed: a
     * result that stops arriving shows up as a screen quietly holding default state, not as
     * a throw. Assert the observable effect — the originating screen reflecting the save —
     * never the absence of an exception, or the test passes vacuously.
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
