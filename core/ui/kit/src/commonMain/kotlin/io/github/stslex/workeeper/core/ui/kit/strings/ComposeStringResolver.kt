// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.strings

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString as composeGetPluralString
import org.jetbrains.compose.resources.getString as composeGetString

/**
 * The app-scoped [StringResolver]: CMP's top-level functions, called exactly as a direct caller
 * would (kmp-string-resolver.md §4.2). With no arguments it calls the no-argument overload, so
 * the catalog text comes back verbatim.
 * GUARD: must stay public — an internal contribution does not aggregate into `AppGraph`.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ComposeStringResolver : StringResolver {

    // GUARD: the import aliases are load-bearing. Members win over imports, so an unqualified
    // `getString(resource, *formatArgs)` in this class calls this member and recurses.
    override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String =
        if (formatArgs.isEmpty()) {
            composeGetString(resource)
        } else {
            composeGetString(resource, *formatArgs)
        }

    override suspend fun getPluralString(
        resource: PluralStringResource,
        quantity: Int,
        vararg formatArgs: Any,
    ): String = if (formatArgs.isEmpty()) {
        composeGetPluralString(resource, quantity)
    } else {
        composeGetPluralString(resource, quantity, *formatArgs)
    }
}
