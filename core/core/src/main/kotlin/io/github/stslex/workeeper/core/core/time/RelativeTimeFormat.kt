// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.time

import android.text.format.DateUtils

/**
 * Formats [eventMillis] as a localized relative-time string ("just now", "5 min ago",
 * "2 days ago", or an absolute date once the gap exceeds the week boundary). Uses
 * Android's [DateUtils] so the output respects the system locale automatically.
 */
fun formatRelativeTime(nowMillis: Long, eventMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        eventMillis,
        nowMillis,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
