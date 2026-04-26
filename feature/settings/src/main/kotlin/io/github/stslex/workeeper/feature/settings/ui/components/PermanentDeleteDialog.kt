// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.domain.model.ArchivedItem

@Composable
internal fun PermanentDeleteDialog(
    target: ArchivedItem,
    impactCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(
        R.string.feature_archive_dialog_permanent_delete_title,
        target.name,
    )
    val body = if (impactCount > 0) {
        pluralStringResource(
            R.plurals.feature_archive_dialog_permanent_delete_body_with_history,
            impactCount,
            impactCount,
        )
    } else {
        stringResource(R.string.feature_archive_dialog_permanent_delete_body_no_history)
    }
    val impactSummary = if (impactCount > 0) {
        pluralStringResource(R.plurals.feature_archive_session_count, impactCount, impactCount)
    } else {
        stringResource(R.string.feature_archive_dialog_impact_summary_empty)
    }
    AppConfirmDialog(
        title = title,
        body = body,
        impactSummary = impactSummary,
        confirmLabel = stringResource(R.string.feature_archive_dialog_confirm_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
