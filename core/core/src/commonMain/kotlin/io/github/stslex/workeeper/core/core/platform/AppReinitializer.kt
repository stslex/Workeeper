// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

/**
 * Platform seam for "reinitialize the app to a clean state" — invoked after a live
 * database-file swap (restore / rollback / undo) leaves the running process's in-memory
 * state inconsistent with the on-disk data. Callers express intent ("reinitialize after
 * this recovery step"); the platform actual owns the mechanism. This is what keeps
 * recovery/domain code free of `android.*`.
 *
 * The Android actual is a full process restart (relaunch the launcher activity, then
 * `Runtime.getRuntime().exit(0)`), so the next process rebuilds the DI graph and reopens
 * Room against the swapped file.
 *
 * The iOS actual throws. iOS cannot restart its own process, so the expected shape is an
 * in-process rebuild (tear down the app graph, rebuild, reopen Room, reset navigation),
 * which is Phase 5's deliverable.
 *
 * That rebuild puts a SECOND app graph in one process, which every app-scoped `DataStore`
 * holder must survive: mint through `DataStoreProvider`'s process-lifetime memoization, never
 * per-instance, or the second graph breaks silently rather than loudly. Adding a holder that
 * mints its own store re-breaks the rebuild without failing any existing test but this one —
 * pinned by `app/app` androidTest `AppScopeDataStoreSingletonTest`. Reinit-order and
 * `AppDialogRepository` preservation notes: documentation/kmp-migration-assessment.md.
 */
expect class AppReinitializer {

    fun reinitialize()
}
