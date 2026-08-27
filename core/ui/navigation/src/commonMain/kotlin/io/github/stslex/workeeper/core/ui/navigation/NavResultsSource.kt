// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.StateFlow

/**
 * Keyed store of nullable result flows behind the typed contract. The value is written before the
 * pop, cleared after delivery, and every other navigation clears every channel.
 */
@Stable
interface NavResultsSource {

    /** The nullable stream for [key]; it exists from first observation, initial value `null`. */
    fun result(key: String): StateFlow<Any?>

    fun setResult(key: String, result: Any)

    fun clearResult(key: String)
}
