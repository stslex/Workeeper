// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.strings

/**
 * Fills a catalog template read verbatim (a [StringResolver] read with no arguments) as Compose
 * resources' `getString(resource, *formatArgs)` fills it: every positional `%N$d` or `%N$s` becomes
 * `formatArgs[N - 1].toString()`, and nothing else in the template changes (Compose resources 1.11.1,
 * `replaceWithArgs`). A missing argument throws [IndexOutOfBoundsException] there and here.
 *
 * Pure and synchronous, so a State lambda or a click path may call it: copy resolved once into
 * State can still take arguments (kmp-phase-7-16-live-workout-feature.md, D5).
 */
fun String.fillTemplate(vararg formatArgs: Any): String =
    POSITIONAL_PLACEHOLDER.replace(this) { match ->
        // GUARD: the lambda inserts the argument literally; a replacement string would read `$1` or
        // a backslash in a user-entered name as a group reference.
        formatArgs[match.groupValues[1].toInt() - 1].toString()
    }

private val POSITIONAL_PLACEHOLDER = Regex("""%(\d+)\$[ds]""")
