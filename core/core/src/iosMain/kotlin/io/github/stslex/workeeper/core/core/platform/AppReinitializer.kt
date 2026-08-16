// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

/**
 * iOS [AppReinitializer]: NOT IMPLEMENTED — throws so a wired-but-unfinished recovery
 * path fails loudly instead of silently skipping the reinit (the Firebase-holder no-op
 * precedent is wrong here: a no-op after a DB-file swap leaves the process serving stale
 * in-memory state). The in-process-rebuild design, and the three DataStore-memoization
 * bypasses that currently make it unsound, are recorded on the expect declaration; the
 * implementation is a Phase 5 deliverable.
 */
actual class AppReinitializer {

    actual fun reinitialize() {
        TODO("iOS reinitialize = in-process graph rebuild; Phase 5. See the expect KDoc.")
    }
}
