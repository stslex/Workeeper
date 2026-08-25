// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.api.model

/**
 * The union of buttons any [AppDialog] variant can present. Per-`(variant, action)` reaction lives
 * in consumer-side handlers, never in the Store's own. See the app-dialogs spec for the mapping.
 */
enum class AppDialogUserAction {
    Acknowledge,
    RequestUndo,
    Report,
    ExportDiagnostics,
    ConfirmUndo,
    Cancel,
}
