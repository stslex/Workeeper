// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.ui

import java.text.DateFormat
import java.util.Date

/**
 * Locale-aware medium-style date formatter shared by the catalog's
 * date-bearing dialogs ([RestoreSuccessDialog], [UndoRestoreConfirmationDialog]).
 * Pulled out of the individual dialog files so the body-string formatting
 * stays consistent across the catalog.
 */
internal fun formatMediumDate(epochMs: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))
