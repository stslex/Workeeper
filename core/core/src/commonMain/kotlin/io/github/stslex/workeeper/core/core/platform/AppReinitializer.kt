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
 * `Runtime.getRuntime().exit(0)`), so the next process rebuilds the runtime generation and
 * opens a fresh Room instance against the swapped file. Phase 5 (R2) locked this as the
 * shipping Android behavior.
 *
 * The iOS actual delegates to the root-bound [AppReinitializationHost] — iOS cannot restart
 * its own process, so reinitialization is an in-process runtime-generation rebuild: quiesce,
 * replace the generation (database + Metro graph + lifetime + UI/navigation ownership handed
 * over as ONE unit — Room 3's `close()` is terminal for the object, measured, so the swapped
 * file gets a NEW `AppDatabase` from the full production factory, never a reopen). The
 * lifecycle mechanism is Phase 5's Android-instrumented deliverable; the iOS database factory,
 * composition root, and host binding are Phase 7's
 * (`kmp-phase-5-startup-processor.md` §8.4/§8.8).
 *
 * A rebuild puts a SECOND runtime generation in one process, which every app-scoped `DataStore`
 * holder must survive: mint through `DataStoreProvider`'s process-lifetime memoization, never
 * per-instance, or the second generation breaks silently rather than loudly. Adding a holder
 * that mints its own store re-breaks the rebuild without failing any existing test but this one
 * — pinned by `app/app` androidTest `AppScopeDataStoreSingletonTest`.
 */
expect class AppReinitializer {

    fun reinitialize()
}
