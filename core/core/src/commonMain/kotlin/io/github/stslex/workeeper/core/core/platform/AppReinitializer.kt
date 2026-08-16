// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

/**
 * Platform seam for "reinitialize the app to a clean state" — invoked after a live
 * database-file swap (restore / rollback / undo) leaves the running process's in-memory
 * state inconsistent with the on-disk data. Callers express intent ("reinitialize after
 * this recovery step"); the platform actual owns the mechanism. This is what keeps
 * recovery/domain code free of `android.*`.
 *
 * The Android actual is a full process restart (relaunch the launcher activity in a fresh
 * task, then `Runtime.getRuntime().exit(0)`): the OS tears down every Activity and the
 * next process rebuilds the DI graph and reopens Room against the swapped file, resetting
 * navigation and in-memory state wholesale.
 *
 * The iOS actual is a Phase 5 deliverable and currently throws. iOS cannot restart its
 * own process (no API; App Store rejects `exit()`), so the likely shape is an
 * **in-process rebuild**: tear down the app graph, rebuild it, reopen Room, reset
 * navigation to root. That is NOT a mechanical rewrite today, and the reason is recorded
 * here so Phase 5 does not rediscover it: three `@SingleIn(AppScope)` classes —
 * `BackupPreferencesRepositoryImpl`, `RestoreStateRepositoryImpl`, `AppDialogRepository` —
 * bypass `DataStoreProvider`'s process-lifetime memoization and construct their DataStore
 * directly, so a second graph in the same process throws
 * "multiple DataStores active for the same file". Worse, that throw is swallowed by the
 * `.catch` in `AppCoroutineScopeImpl.launch(flow, …)`, so the symptom is silently missing
 * data, not a crash. Until those three ride the memoized provider (each needs a new
 * module edge — see tech-debt.md "DataStore singleton bypass"), an in-process rebuild is
 * unsound. See also the reinit-order + `AppDialogRepository` preservation note in
 * documentation/kmp-migration-assessment.md.
 */
expect class AppReinitializer {

    fun reinitialize()
}
