// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui.components

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.feature.archive.domain.model.ArchivedItem
import io.github.stslex.workeeper.feature.archive.resources.Res
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_dialog_confirm_delete
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_dialog_impact_summary_empty
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_dialog_permanent_delete_body_no_history
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_dialog_permanent_delete_body_with_history
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_dialog_permanent_delete_title
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_session_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PermanentDeleteDialog(
    target: ArchivedItem,
    impactCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(
        Res.string.feature_archive_dialog_permanent_delete_title,
        target.name,
    )
    val body = if (impactCount > 0) {
        pluralStringResource(
            Res.plurals.feature_archive_dialog_permanent_delete_body_with_history,
            impactCount,
            impactCount,
        )
    } else {
        stringResource(Res.string.feature_archive_dialog_permanent_delete_body_no_history)
    }
    val impactSummary = if (impactCount > 0) {
        // GUARD: Compose resources substitute only positional `%1$d`; this catalog's bare `%d`
        // is filled here, as Android's String.format did, so the catalog moves byte-exact.
        pluralStringResource(Res.plurals.feature_archive_session_count, impactCount)
            .replace("%d", impactCount.toString())
    } else {
        stringResource(Res.string.feature_archive_dialog_impact_summary_empty)
    }
    AppConfirmDialog(
        title = title,
        body = body,
        impactSummary = impactSummary,
        confirmLabel = stringResource(Res.string.feature_archive_dialog_confirm_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
