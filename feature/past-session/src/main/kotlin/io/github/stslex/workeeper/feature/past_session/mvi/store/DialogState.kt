// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.store

import androidx.compose.runtime.Stable

/**
 * The screen's modal dialogs, mutually exclusive by construction (`mvi-dialog-state`,
 * Rule 4 of the compose state discipline). Replaces the single `deleteDialogVisible`
 * boolean the moment the screen gained a second dialog: the PR explainer used to live in a
 * composable-local `remember`, which is exactly the Rule-4 violation the sealed shape
 * retires.
 */
@Stable
sealed interface DialogState {

    @Stable
    data object Hidden : DialogState

    /** "Delete session?" — the destructive confirmation behind the overflow menu item. */
    @Stable
    data object DeleteConfirm : DialogState

    /** The personal-record explainer, opened from a row's PR tag (extraction §2.7). */
    @Stable
    data object PrExplainer : DialogState
}

/**
 * The screen's bottom sheets. One today — the topbar `⋮` menu (extraction §2.2 draws the
 * glyph and no target; the session screen's `sh-session` pattern supplies the shape).
 */
@Stable
sealed interface BottomSheetState {

    @Stable
    data object Hidden : BottomSheetState

    @Stable
    data object SessionMenu : BottomSheetState
}
