// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui.components

import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

private const val SECONDS_IN_MINUTE = 60L

/**
 * Format an elapsed duration for the live workout header / banner. Uses `HH:MM:SS` once
 * the workout passes the one-hour mark, otherwise renders `MM:SS` to keep the timer
 * compact on the header chip.
 */
internal fun formatElapsed(millis: Long): String {
    val total = millis.coerceAtLeast(0L).milliseconds
    val hours = total.inWholeHours
    val minutes = total.inWholeMinutes % SECONDS_IN_MINUTE
    val seconds = total.inWholeSeconds % SECONDS_IN_MINUTE
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
