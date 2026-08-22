// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

/**
 * iOS [AppReinitializer]: delegates to the root-bound [AppReinitializationHost] — iOS cannot
 * restart its own process, so reinitialization is an in-process runtime-generation rebuild
 * (Phase 5 R2 model; the lifecycle mechanism is implemented and proven on Android, and the iOS
 * database factory + composition root + host binding are Phase 7's).
 *
 * The host is a CONSTRUCTOR requirement, deliberately: there is no silent no-op and no runtime
 * `TODO()` — a composition root that has no real host cannot construct this class at all, so a
 * wired-but-unfinished recovery path fails at the root's construction, loudly and immediately,
 * not at the moment a user's restore needed the reinit (the Firebase-holder no-op precedent is
 * wrong here: a no-op after a DB-file swap leaves the process serving stale in-memory state).
 */
actual class AppReinitializer(
    private val host: AppReinitializationHost,
) {

    actual fun reinitialize() {
        host.requestReinitialize()
    }
}
