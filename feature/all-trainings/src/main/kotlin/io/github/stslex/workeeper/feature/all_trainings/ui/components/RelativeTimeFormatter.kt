// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.feature.all_trainings.R

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

/**
 * Compact "Xm / Xh / Xd ago" labels used by Trainings tab status lines. Resolves the
 * strings via [stringResource] so RU plural forms come from `values-ru`.
 */
@Composable
internal fun rememberRelativeTimeLabel(
    timestamp: Long,
    nowProvider: () -> Long = { System.currentTimeMillis() },
): String {
    val justNow = stringResource(R.string.feature_all_trainings_relative_just_now)
    val minutes = stringResource(R.string.feature_all_trainings_relative_minutes_format)
    val hours = stringResource(R.string.feature_all_trainings_relative_hours_format)
    val days = stringResource(R.string.feature_all_trainings_relative_days_format)
    return remember(timestamp) {
        formatRelativeAgo(
            timestamp = timestamp,
            now = nowProvider(),
            justNow = justNow,
            minutesFormat = minutes,
            hoursFormat = hours,
            daysFormat = days,
        )
    }
}

internal fun formatRelativeAgo(
    timestamp: Long,
    now: Long,
    justNow: String,
    minutesFormat: String,
    hoursFormat: String,
    daysFormat: String,
): String {
    val deltaMs = (now - timestamp).coerceAtLeast(0L)
    return when {
        deltaMs < MINUTE_MS -> justNow
        deltaMs < HOUR_MS -> minutesFormat.format(deltaMs / MINUTE_MS)
        deltaMs < DAY_MS -> hoursFormat.format(deltaMs / HOUR_MS)
        else -> daysFormat.format(deltaMs / DAY_MS)
    }
}
