package io.github.stslex.workeeper.core.core.time

import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

private const val SECONDS_IN_MINUTE = 60L

/**
 * Formats elapsed workout duration as `MM:SS` and switches to `H:MM:SS` after 1 hour.
 */
fun formatElapsedDuration(millis: Long, locale: Locale = Locale.getDefault()): String {
    val total = millis.coerceAtLeast(0L).milliseconds
    val hours = total.inWholeHours
    val minutes = total.inWholeMinutes % SECONDS_IN_MINUTE
    val seconds = total.inWholeSeconds % SECONDS_IN_MINUTE
    return if (hours > 0L) {
        String.format(locale, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(locale, "%02d:%02d", minutes, seconds)
    }
}
