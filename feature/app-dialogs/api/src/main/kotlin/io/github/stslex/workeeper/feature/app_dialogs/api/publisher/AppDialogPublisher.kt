// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.api.publisher

import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog

/**
 * Producer-side contract for the cross-feature [AppDialog] catalog. Any feature
 * that needs to surface a process-survival modal calls [publish]; rendering is
 * owned by `feature/app-dialogs/impl` and consumes the same DataStore-backed
 * state.
 *
 * There is intentionally **no** `cancel` / `clear` / `overwrite` method. Dismiss
 * is user-driven only — a producer that wants to "withdraw" a dialog either
 * shouldn't have published, or should set a sibling DataStore flag that its own
 * subscription observes (see
 * `documentation/feature-specs/app-dialogs.md` → "AppDialogPublisher contract").
 */
interface AppDialogPublisher {

    /**
     * Persist [dialog] to DataStore so it surfaces on the next composition of
     * `AppDialogHost` — including after process restart.
     *
     * **Dedup contract:** publish is idempotent per variant. If the variant's
     * primary flag is already set in DataStore, this call is a no-op and the
     * first payload wins. The read-then-write is atomic against concurrent
     * producers because both happen inside a single `dataStore.edit { ... }`
     * block. Dedup is **per-variant** — a pending [AppDialog.RestoreFailure]
     * does not block a [AppDialog.RestoreSuccess] publish.
     *
     * Locking the first-payload-wins trade-off in: realistic repeat publishes
     * carry the same upstream cause and the same payload, so dropping the
     * duplicate is correct. The alternative (overwrite) would let a second
     * call mutate the dialog body mid-read.
     */
    suspend fun publish(dialog: AppDialog)
}
