// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.strings

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

/**
 * Compose-resource strings for code outside composition (kmp-string-resolver.md); mirrors CMP's
 * non-composable `getString` / `getPluralString`. It reads the system resource environment, so it
 * picks the primary locale only and formats arguments with `toString()`; only positional
 * placeholders (`%1$s`, `%1$d`) are filled (architecture.md → Localization). Never called inside a
 * State lambda and never injected into domain code; tests fake it.
 */
interface StringResolver {

    suspend fun getString(resource: StringResource, vararg formatArgs: Any): String

    suspend fun getPluralString(
        resource: PluralStringResource,
        quantity: Int,
        vararg formatArgs: Any,
    ): String
}
