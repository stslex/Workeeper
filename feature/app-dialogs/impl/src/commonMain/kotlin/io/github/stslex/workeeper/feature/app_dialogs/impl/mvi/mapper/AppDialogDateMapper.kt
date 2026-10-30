// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog

/**
 * D3: the medium date a dialog's body shows, formatted before the State write: the restore's date
 * for a restore success, the brought-back data's date for an undo confirmation, and nothing for a
 * dialog without a date.
 */
internal fun AppDialog?.toDateLabel(resourceWrapper: ResourceWrapper): String = when (this) {
    is AppDialog.RestoreSuccess -> resourceWrapper.formatMediumDate(restoredAtEpochMs)
    is AppDialog.UndoRestoreConfirmation -> resourceWrapper.formatMediumDate(originalDataDateEpochMs)
    is AppDialog.RestoreFailure, is AppDialog.UndoRestoreSuccess, null -> ""
}
