// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.snackbar

import androidx.compose.ui.platform.AccessibilityManager

/**
 * How long a toast stays up: the drawn 5000ms, timed by the host rather than by an M3 rung.
 *
 * GUARD: do not simplify to `duration = SnackbarDuration.Long` — `Indefinite` short-circuits
 * Compose's accessibility timeout, so this call is the only thing applying the user's stretch.
 */
fun toastTimeoutMillis(
    accessibilityManager: AccessibilityManager?,
    hasAction: Boolean,
): Long = accessibilityManager?.calculateRecommendedTimeoutMillis(
    originalTimeoutMillis = TOAST_VISIBLE_MS,
    containsIcons = false,
    containsText = true,
    containsControls = hasAction,
) ?: TOAST_VISIBLE_MS

/** `session-v3f.html` `toast()`: `setTimeout(() => el.classList.remove('on'), 5000)`. */
const val TOAST_VISIBLE_MS: Long = 5_000L
