// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.feature.settings.domain.model.ArchivedItem

@Composable
internal fun PermanentDeleteDialog(
    target: ArchivedItem,
    impactCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val typeWord = when (target) {
        is ArchivedItem.Exercise -> "exercise"
        is ArchivedItem.Training -> "training"
    }
    val title = "Delete '${target.name}' permanently?"
    val body = if (impactCount > 0) {
        "This will permanently delete the $typeWord along with $impactCount " +
            "${if (impactCount == 1) "session" else "sessions"} of history. " +
            "This cannot be undone."
    } else {
        "This will permanently delete the $typeWord. This cannot be undone."
    }
    val impactSummary = if (impactCount > 0) {
        "$impactCount ${if (impactCount == 1) "session" else "sessions"} of history"
    } else {
        "No session history affected"
    }
    AppConfirmDialog(
        title = title,
        body = body,
        impactSummary = impactSummary,
        confirmLabel = "Delete",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
