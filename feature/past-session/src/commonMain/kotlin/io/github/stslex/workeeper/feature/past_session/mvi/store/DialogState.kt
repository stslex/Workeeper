// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.store

import androidx.compose.runtime.Stable

/** The screen's modal dialogs, mutually exclusive by construction (see `mvi-dialog-state`). */
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

/** The screen's bottom sheets. One today — the topbar `⋮` menu. */
@Stable
sealed interface BottomSheetState {

    @Stable
    data object Hidden : BottomSheetState

    @Stable
    data object SessionMenu : BottomSheetState
}
