// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker

/** DB-work admission binding dependencies to one generation; [release] belongs in the worker finally. */
interface BackupWorkLease {

    /** The admitted generation's worker dependencies — read them only through this lease. */
    val deps: BackupWorkerDeps

    fun release()
}
