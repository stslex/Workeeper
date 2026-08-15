// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.StateFlow

/**
 * The result transport behind the typed contract, post-Nav2.
 *
 * Under Nav2 a result travelled on the previous entry's `SavedStateHandle`; Nav3 has no result
 * API and no library-owned per-entry handle, so the transport lives inside the Navigator
 * implementation — a keyed store of nullable flows, written by `popBackWithResult` BEFORE the pop
 * (the consumer recomposes on return; write-after-pop loses the value, same ordering rule the
 * Nav2 adapter documented) and cleared by the consumer after delivery.
 *
 * The consumer surface (`NavResults` in `core:ui:mvi`) is unchanged: nullable read, `null` means
 * "no result", cleared after invoke. Keys are [NavResultKey.of] strings, exactly as before.
 *
 * **Accepted delta vs Nav2:** a result no longer survives process death inside the set→collect
 * window. The window is one recomposition wide; the `SavedStateHandle` transport covered it by
 * construction, this one does not, and no user journey holds a result across process death.
 */
@Stable
interface NavResultsSource {

    /** The nullable stream for [key]; a flow exists from first observation, initial value `null`. */
    fun result(key: String): StateFlow<Any?>

    /** Publish [result] under [key]. Called BEFORE the pop that reveals the consumer. */
    fun setResult(key: String, result: Any)

    /** Reset [key] to `null` after delivery so re-entry does not re-deliver. */
    fun clearResult(key: String)
}
