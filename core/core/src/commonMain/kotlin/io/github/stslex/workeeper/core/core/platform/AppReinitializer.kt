// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

/**
 * Platform-neutral seam for "reinitialize the app to a clean state" — invoked after a
 * live database-file swap (restore / rollback / undo) leaves the running process's
 * in-memory state inconsistent with the on-disk data.
 *
 * The Android actual (`AndroidAppReinitializer`) is a full process restart (kill +
 * relaunch): the OS tears down every Activity and the next process rebuilds the DI
 * graph and reopens Room against the swapped file, resetting navigation and in-memory
 * state wholesale. iOS cannot restart its own process (no API; App Store rejects it),
 * so its future actual performs an in-place reinit (reopen Room → reset navigation to
 * root → invalidate in-memory state) — see the reinit-order + `AppDialogRepository`
 * preservation note in documentation/kmp-migration-assessment.md.
 *
 * Callers express intent ("reinitialize after this recovery step"); the platform owns
 * the mechanism. This is what keeps recovery/domain code free of `android.*`.
 */
interface AppReinitializer {

    fun reinitialize()
}
