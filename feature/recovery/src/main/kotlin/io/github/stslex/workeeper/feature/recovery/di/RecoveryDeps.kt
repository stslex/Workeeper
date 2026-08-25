// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.di

import io.github.stslex.workeeper.core.core.platform.AppReinitializer
import io.github.stslex.workeeper.core.data.backup.api.RecoveryDiagnosticsExporter
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreRecoveryFiles
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.feature.recovery.InterruptedRestoreChecker
import io.github.stslex.workeeper.feature.recovery.diagnostics.RestoreDiagnosticsExport
import io.github.stslex.workeeper.feature.recovery.domain.RestoreRecoveryCoordinator
import io.github.stslex.workeeper.feature.recovery.domain.StartupMigrationCoordinator

/** The app-scope types `RecoveryActivity` reads, acquired via [RecoveryDepsHolder]. */
interface RecoveryDeps {
    val recoveryDiagnosticsExporter: RecoveryDiagnosticsExporter
    val restoreRecoveryFiles: RestoreRecoveryFiles
    val restoreStateRepository: RestoreStateRepository
    val interruptedRestoreChecker: InterruptedRestoreChecker
    val appReinitializer: AppReinitializer
    val restoreRecoveryCoordinator: RestoreRecoveryCoordinator
    val startupMigrationCoordinator: StartupMigrationCoordinator

    /** Scenario-1 export, shared with the `RestoreFailure` dialog reactor. */
    val restoreDiagnosticsExport: RestoreDiagnosticsExport
}

/** Held-instance seam: `BaseApplication` exposes the app-scope graph typed as [RecoveryDeps]. */
interface RecoveryDepsHolder {
    fun recoveryDeps(): RecoveryDeps
}
