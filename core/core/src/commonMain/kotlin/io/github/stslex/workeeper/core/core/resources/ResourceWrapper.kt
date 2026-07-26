// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.resources

import kotlin.time.Clock

/**
 * Platform-neutral access to string/plural resources and locale-aware date formatting.
 *
 * The resource ids are plain [Int]s at this KMP-common boundary — the Android
 * implementation (`AndroidResourceWrapper` in `core:core-android`) treats them as
 * `@StringRes` / `@PluralsRes` and resolves them against a `Context`.
 */
interface ResourceWrapper {

    fun getString(id: Int, vararg args: Any): String

    fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String

    fun getAbbreviatedRelativeTime(
        timestamp: Long,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): String

    fun formatMediumDate(timestamp: Long): String
}
