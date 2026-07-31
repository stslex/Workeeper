// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.snackbar

import androidx.compose.ui.platform.AccessibilityManager

/**
 * How long a toast stays up — **the drawn number, timed by us.**
 *
 * `session-v3f.html`'s `toast()` expires the panel with `setTimeout(…, 5000)`, and `AppToast`'s
 * KDoc already assigns that away: *"Presentation (placement, the 5s auto-dismiss, entrance motion)
 * belongs to the host — this is only the panel."* The host never implemented it, so Material3's
 * default filled the vacuum: `showSnackbar`'s `duration` defaults to `Indefinite` whenever an
 * `actionLabel` is present, and three toasts carrying an undo simply never went away (B25).
 *
 * ## Why the host times this instead of naming an M3 duration
 *
 * There is no rung to name. `SnackbarDuration` offers `Short` (4000ms) and `Long` (10000ms);
 * the drawing says **5000**. Rounding to a rung would be transcribing a different number than the
 * one drawn, so the timing moves here and the divergence is a recorded decision rather than a
 * silent 4s or 10s. [ToastDurationTest] asserts that 5000 is neither rung, so the divergence
 * cannot be quietly "corrected" back onto one.
 *
 * ## This function is an inversion of the framework path, on purpose
 *
 * The host shows the snackbar as `Indefinite` and then calls the accessibility manager **itself**,
 * which is exactly backwards from how Material3 is meant to be used, and it will read as
 * over-engineering to anyone meeting it cold. It is not. It is the only arrangement that gets both
 * halves:
 *
 * - Naming a real `SnackbarDuration` would let M3 do the accessibility call for us — but only at
 *   4000ms or 10000ms, and the drawing says 5000.
 * - Timing 5000ms ourselves means M3 must not also be running a timer, hence `Indefinite`.
 * - And `Indefinite` is the one duration that **silently skips accessibility**, so the call has to
 *   move here or it does not happen at all.
 *
 * ## The accessibility half, which is the part the intuition gets backwards
 *
 * `Indefinite` looks like the *maximum* courtesy to a user who needs more time, and it is the
 * opposite. Compose's `AndroidAccessibilityManager.calculateRecommendedTimeoutMillis` opens with
 * `if (originalTimeoutMillis >= Int.MAX_VALUE) return originalTimeoutMillis`, and `Indefinite`
 * maps to `Long.MAX_VALUE` — so an indefinite snackbar **short-circuits before the system manager
 * is consulted** and never receives the stretch a display-timeout preference has asked for.
 *
 * [containsControls] tracks whether the toast carries an action, because that is the flag the
 * platform reads to decide the user needs time to *reach* a control, not merely to read text.
 *
 * ## DO NOT "simplify" this to `duration = SnackbarDuration.Long`
 *
 * That change compiles, deletes this function and the `withTimeoutOrNull` in `App.kt`, looks like
 * an obvious tidy-up in review — and **silently removes the accessibility stretch**, because the
 * user-visible behaviour is identical for everyone who has not set a display-timeout preference.
 * It also moves the toast from the drawn 5000ms to 10000ms without saying so.
 *
 * `ToastDurationTest` is the gate, and it was proven to fire rather than assumed: dropping the
 * accessibility manager reddens three of its five cases, and rounding [TOAST_VISIBLE_MS] onto an
 * M3 rung reddens the other two. The pointer is here so the gate is findable **before** the
 * simplification is attempted rather than after it goes red (B25, §10.4).
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
