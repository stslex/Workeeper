// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

/**
 * The root-bound intent contract for restart-free reinitialization (KMP Phase 5, R2 —
 * `kmp-phase-5-startup-processor.md` §8.8): "rebuild the app's runtime generation in this
 * process". The HOST that can actually do that — the application-owned runtime that owns the
 * database, graph, lifetime, and UI-generation handover — implements this interface; platform
 * `AppReinitializer` actuals that cannot restart their process (iOS) delegate to it.
 *
 * Direction of the dependency is the whole point: this interface lives in `core:core` and the
 * runtime host (an application-module concern) implements it downward. `core:core` never learns
 * who the host is; a composition root binds one. Phase 5 proves the Android host; Phase 7's
 * `iosApp` composition root constructs the iOS database + graph and binds ITS host — until then
 * nothing constructs the iOS [AppReinitializer], and its host-requiring constructor is what makes
 * "wired but unimplemented" a compile-time impossibility instead of a runtime surprise.
 */
interface AppReinitializationHost {

    /**
     * Fire-and-forget intent: replace the current runtime generation. The host serializes and
     * coalesces concurrent requests; callers get the platform contract of
     * [AppReinitializer.reinitialize] — after this, the process serves a freshly built
     * generation (or, on Android production, a freshly relaunched process).
     */
    fun requestReinitialize()
}
