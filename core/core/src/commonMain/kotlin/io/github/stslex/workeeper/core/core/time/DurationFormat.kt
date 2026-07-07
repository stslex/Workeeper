package io.github.stslex.workeeper.core.core.time

import kotlin.time.Duration.Companion.milliseconds

private const val SECONDS_IN_MINUTE = 60L
private const val PAD_WIDTH = 2

/**
 * Formats elapsed workout duration as `MM:SS` and switches to `H:MM:SS` after 1 hour.
 *
 * The output is a fixed digit-and-colon grammar with no locale-sensitive separators, so
 * it is formatted with pure-Kotlin zero-padding — no `java.util.Locale` / `String.format`,
 * keeping this callable from KMP `commonMain`.
 */
fun formatElapsedDuration(millis: Long): String {
    val total = millis.coerceAtLeast(0L).milliseconds
    val hours = total.inWholeHours
    val minutes = total.inWholeMinutes % SECONDS_IN_MINUTE
    val seconds = total.inWholeSeconds % SECONDS_IN_MINUTE
    val mm = minutes.toString().padStart(PAD_WIDTH, '0')
    val ss = seconds.toString().padStart(PAD_WIDTH, '0')
    return if (hours > 0L) {
        "$hours:$mm:$ss"
    } else {
        "$mm:$ss"
    }
}
