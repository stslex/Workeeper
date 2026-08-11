// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.di

import io.github.stslex.workeeper.core.data.backup.api.RecoveryDiagnosticsExporter
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider

/**
 * feature/recovery's dep interface for the god-object split (variant A, mechanism A). Names the exact two
 * app-scope types [RecoveryActivity][io.github.stslex.workeeper.feature.recovery.RecoveryActivity] reads —
 * no spine (it is a framework-instantiated `Activity`, not a Store `Feature`, so it needs none of the
 * MVI/navigation plumbing a feature `Feature` object pulls in), no dispatchers/qualifiers.
 *
 * ACQUISITION — TYPED POINT-ACQUISITION, not `appDeps<T>()`: `RecoveryActivity` uses **zero** `core:ui:mvi`
 * symbols and would only have reached the mvi-homed `appDeps<T>()` transitively through the now-deleted
 * `core:di`. Rather than add a parasitic `core:ui:mvi` edge to an Activity-only module, this
 * module hosts its own concrete typed holder ([RecoveryDepsHolder]) — the same layer-appropriate pattern the
 * data-layer `MetroWorkerFactory` uses. `BaseApplication` implements the holder; `appGraph` (which
 * implements this interface) is handed back typed as `RecoveryDeps`.
 *
 * Both types are owned by modules `feature/recovery` ALREADY depends on directly
 * (`DatabaseSnapshotProvider` → `core:data:database`; `RecoveryDiagnosticsExporter` →
 * `core:data:backup:api`) — no new edge, no cycle, and no reliance on the deleted `core:di`.
 */
interface RecoveryDeps {
    val databaseSnapshotProvider: DatabaseSnapshotProvider
    val recoveryDiagnosticsExporter: RecoveryDiagnosticsExporter
}

/**
 * Held-instance seam for [RecoveryDeps]: the process `Application` exposes the app-scope graph typed as
 * [RecoveryDeps]. Returns the concrete interface (NOT `Any`) — no reified generic, no unchecked cast: the
 * Worker/Recovery framework readers each need exactly one interface, so a concrete typed holder is cleaner
 * than the generic `AppDepsHolder`/`appDeps<T>()` wrapper and is the accepted layer-specific acquisition for
 * modules that must not (or need not) depend on `core:ui:mvi`.
 *
 * `RecoveryActivity` reads it via `(applicationContext as RecoveryDepsHolder).recoveryDeps()` — the cast is
 * safe by construction because `BaseApplication : RecoveryDepsHolder` (compile-visible).
 */
interface RecoveryDepsHolder {
    fun recoveryDeps(): RecoveryDeps
}
