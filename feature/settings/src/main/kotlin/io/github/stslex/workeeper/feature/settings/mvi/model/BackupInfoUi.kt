// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.model

import androidx.compose.runtime.Immutable

/**
 * What is known about the account's stored backups — **three states, because there are three.**
 *
 * ## The defect this replaces
 *
 * This was `BackupInfoUi?`, and the nullable carried two meanings at once: `null` meant *not yet
 * loaded*, while `BackupInfoUi(isEmpty = true)` meant *known to have none*. The UI could not tell
 * them apart, so a signed-in account with an unfinished Drive round-trip rendered identically to
 * one with no backups.
 *
 * That is not a frame-scale problem and a loading deferral is the wrong instrument for it.
 * `BackupClickHandler.observeAuth()` commits `backupAuth = Authenticated` **immediately** and only
 * then calls `loadBackupList()`, whose `onSuccess` writes the info in a **second** emission — with a
 * network round-trip in between. The screen renders both emissions faithfully; what was wrong was
 * that the first one had no way to say "I do not know yet".
 *
 * ## The audit, so this is not mistaken for a screen's bug or for a codebase-wide one
 *
 * `null`-as-not-yet-loaded is an idiom, so every nullable field on every `Store.State` was checked
 * for the same conflation — 30 fields. **This is the only one**, because everywhere else either a
 * discriminator already exists (`isLoading` in exercise, chart, live-workout, single-training;
 * `isActiveLoaded` in home; `backupAuth` for `backupPreferences`) or only one meaning is reachable
 * (`archivedCounts` is `null` until known and `(0, 0)` when zero; pending-dialog fields and route
 * arguments are absence-only, with no load involved). Settings is the one State class carrying no
 * loading discriminator of any kind.
 *
 * ## What [Unknown] draws is NOT decided here
 *
 * This type makes the third state expressible and forces a call site to answer it. It does not
 * choose the treatment: `#s-set` draws the populated row and nothing else, so a row-level spinner,
 * a withheld sub-line or a skeleton are all §0.1 decisions on an undrawn surface. Until the mockup
 * pass rules, [Unknown] renders exactly what `null` rendered — no sub-line — so this change moves
 * zero pixels, and the settings goldens prove it.
 */
@Immutable
sealed interface BackupInfoUi {

    /** The Drive round-trip has not answered yet. Distinct from [Empty], which IS an answer. */
    data object Unknown : BackupInfoUi

    /**
     * The account has zero stored backups.
     *
     * Carries only [backupCountText]: composing it with a "last backup" line reads as a redundant
     * double statement («Ещё нет резервных копий · Резервных копий ещё нет»), and the mockup draws
     * the separator only for the populated case. That was the old `isEmpty` flag's whole job, and
     * it is now the difference between two variants rather than a boolean inside one.
     */
    data class Empty(val backupCountText: String) : BackupInfoUi

    /** At least one stored backup. Both texts, joined by the drawn interpunct. */
    data class Present(
        val lastBackupText: String,
        val backupCountText: String,
    ) : BackupInfoUi
}
