// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.model

import androidx.compose.runtime.Immutable

/**
 * What is known about the account's stored backups — **three states, because there are three.**
 *
 * [Unknown] is *not yet loaded*, [Empty] is *known to have none*, and they are different claims: a
 * signed-in account with an unfinished Drive round-trip is in the first, not the second. **Do not
 * collapse them.** Reporting [Empty] for a failed or pending list call turns a network condition
 * into a statement about the user's account, which is the one thing this type exists to prevent.
 *
 * **A loading deferral is the wrong instrument here and will not help.**
 * `BackupClickHandler.observeAuth()` commits `backupAuth = Authenticated` immediately and only then
 * calls `loadBackupList()`, whose `onSuccess` writes the info in a *second* emission with a network
 * round-trip in between. Both emissions are rendered faithfully; the gap is not frame-scale, so
 * `rememberDeferredSurface` does not apply — the first emission needs a way to *say* "not known
 * yet", which is [Unknown].
 *
 * This is the only `Store.State` nullable on the arc carrying that conflation; the audit behind
 * that claim is §24.2, mockup-pass item 7.
 *
 * ## What [Unknown] draws is NOT decided here
 *
 * This type makes the third state expressible and forces a call site to answer it. It does not
 * choose the treatment: `#s-set` draws the populated row and nothing else, so a row-level spinner,
 * a withheld sub-line or a skeleton are all §0.1 decisions on an undrawn surface. Until the mockup
 * pass rules, [Unknown] renders what the screen rendered before it existed — no sub-line — so the
 * settings goldens are byte-identical across the change.
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
