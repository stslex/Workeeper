// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

/**
 * Platform/runtime information the domain layer needs (app version, device model)
 * without importing platform SDK types. An expect/actual class, not an interface with a
 * DI binding: consumers inject the class itself and each platform's actual owns
 * construction (Android reads `PackageManager` + `Build`; iOS reads the main-bundle info
 * dictionary + `UIDevice`).
 */
expect class PlatformInfoProvider {

    /** Human-readable app version name (e.g. "1.47.0"); empty string if unavailable. */
    fun appVersionName(): String

    /** Monotonic app version code (e.g. 48). */
    fun appVersionCode(): Long

    /**
     * Device model identifier for crash / diagnostics context.
     *
     * TODO(tech-debt): device-model granularity — Android's bare `Build.MODEL` is not
     * strictly equivalent to iOS `UIDevice.model`. Adequate for crash context; revisit
     * normalization when the iOS value is first consumed.
     */
    fun deviceModel(): String
}
