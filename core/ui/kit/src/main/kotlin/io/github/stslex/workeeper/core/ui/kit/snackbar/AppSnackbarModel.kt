// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.snackbar

import androidx.compose.runtime.Stable

/**
 * One transient message. **There is deliberately no `withDismissAction` here.**
 *
 * There used to be, and it was a lie at every call site that set it: `AppSnackbar` maps
 * [AppSnackbarModel] onto `AppToast`, which draws a message and an optional action and nothing
 * else — because `session-v3f.html`'s `.toast` is `<span>` + `<button>Отменить</button>` with no
 * dismiss mark anywhere. Two features passed `withDismissAction = true` and were silently ignored.
 *
 * Material3 recommends that flag specifically for a `SnackbarDuration.Indefinite` snackbar, which
 * is what the host used to end up with. Under B25 branch B the host times the toast itself at the
 * drawn 5000ms ([TOAST_VISIBLE_MS]), so the recommendation no longer applies and the honest
 * resolution is that the parameter goes rather than that an undrawn glyph arrives — adding a
 * dismiss mark would be an appearance decision, and §0.1 gives those to the drawing.
 */
@Stable
data class AppSnackbarModel(
    val message: String,
    val actionLabel: String? = null,
    val action: () -> Unit = { },
)
