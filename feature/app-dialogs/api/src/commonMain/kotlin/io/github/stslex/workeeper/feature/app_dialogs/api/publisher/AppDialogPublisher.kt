// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.api.publisher

import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog

/**
 * Producer-side contract for the [AppDialog] catalog. There is intentionally no cancel/clear —
 * dismiss is user-driven only. See documentation/feature-specs/app-dialogs.md.
 */
interface AppDialogPublisher {

    /**
     * Persist [dialog] so it surfaces on the next `AppDialogHost` composition, including after a
     * process restart. Idempotent per variant: first payload wins, atomic inside one `edit {}`.
     */
    suspend fun publish(dialog: AppDialog)
}
