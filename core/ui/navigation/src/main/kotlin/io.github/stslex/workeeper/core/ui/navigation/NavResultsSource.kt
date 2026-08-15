// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.StateFlow

/**
 * The result transport behind the typed contract — a keyed store of nullable flows inside the
 * Navigator implementation, because the navigation layer owns no per-entry transport of its own.
 *
 * Two ordering invariants, both load-bearing:
 * - the value is written by `popBackWithResult` BEFORE the pop — the consumer recomposes on
 *   return, and a write-after-pop loses the value;
 * - the consumer clears after delivery, so re-entry does not re-deliver.
 *
 * The consumer surface is `NavResults` (`core:ui:mvi`): nullable read, `null` means "no result".
 * Keys are [NavResultKey.of] strings.
 *
 * **Known limitation, accepted by design:** a result does NOT survive process death inside the
 * set→collect window. The window is one recomposition wide and no user journey holds a result
 * across process death. Derivation and the decision record:
 * `documentation/feature-specs/nav3-stage-1-3.md` §3.6.
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
