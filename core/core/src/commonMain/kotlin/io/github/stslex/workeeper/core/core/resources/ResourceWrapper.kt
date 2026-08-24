// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.resources

import kotlin.time.Clock

/**
 * Platform-neutral access to string/plural resources and locale-aware date formatting. Resource ids
 * are plain [Int]s here; the Android impl treats them as `@StringRes` / `@PluralsRes`.
 */
interface ResourceWrapper {

    fun getString(id: Int, vararg args: Any): String

    fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String

    fun getAbbreviatedRelativeTime(
        timestamp: Long,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): String

    fun formatMediumDate(timestamp: Long): String

    /** Day + full month, no year — `22 июля` / `July 22`. */
    fun formatDayMonth(timestamp: Long): String
}
