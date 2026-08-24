// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.di

import io.github.stslex.workeeper.core.data.backup.api.RecoveryDiagnosticsExporter
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.feature.recovery.diagnostics.RestoreDiagnosticsExport

/** The app-scope types `RecoveryActivity` reads, acquired via [RecoveryDepsHolder]. */
interface RecoveryDeps {
    val databaseSnapshotProvider: DatabaseSnapshotProvider
    val recoveryDiagnosticsExporter: RecoveryDiagnosticsExporter

    /** Scenario-1 export, shared with the `RestoreFailure` dialog reactor. */
    val restoreDiagnosticsExport: RestoreDiagnosticsExport
}

/** Held-instance seam: `BaseApplication` exposes the app-scope graph typed as [RecoveryDeps]. */
interface RecoveryDepsHolder {
    fun recoveryDeps(): RecoveryDeps
}
