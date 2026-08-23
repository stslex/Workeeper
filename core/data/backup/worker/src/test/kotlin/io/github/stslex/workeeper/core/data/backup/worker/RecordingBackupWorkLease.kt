// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker

import java.util.concurrent.atomic.AtomicInteger

/**
 * Test double for [BackupWorkLease] counting [release] calls. Minted by the test Applications'
 * [BackupWorkerDepsHolder.awaitBackupWorkLease] overrides, which record every acquisition in an
 * `acquiredLeases` list — so tests assert BOTH sides of the admission contract: how many leases
 * a run acquired (holder-side list) and that each was released exactly once ([releaseCount]).
 */
internal class RecordingBackupWorkLease(
    override val deps: BackupWorkerDeps,
) : BackupWorkLease {

    val releaseCount = AtomicInteger(0)

    override fun release() {
        releaseCount.incrementAndGet()
    }
}
