// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.model

import androidx.compose.runtime.Stable

/**
 * D2: the click paths' constant copy — the weight-wipe confirm, the set-removed and tag-limit
 * toasts, and the deferred delete's message and undo label — resolved once per Store by `Init`,
 * so the click paths that show it never suspend.
 */
@Stable
data class ExerciseClickCopy(
    val typeChangeTitle: String,
    val typeChangeBody: String,
    val typeChangeImpact: String,
    val typeChangeConfirm: String,
    val setRemoved: String,
    val tagLimit: String,
    val permanentDeleteSuccess: String,
    val undoLabel: String,
) {

    companion object {

        /** Until `Init`'s resolution lands; each reader needs a draft, a full chip row or a confirm. */
        val EMPTY = ExerciseClickCopy(
            typeChangeTitle = "",
            typeChangeBody = "",
            typeChangeImpact = "",
            typeChangeConfirm = "",
            setRemoved = "",
            tagLimit = "",
            permanentDeleteSuccess = "",
            undoLabel = "",
        )
    }
}
