// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

/**
 * Platform/runtime information the domain layer needs (app version, device model)
 * without importing Android SDK types. The Android implementation reads
 * `PackageManager` + `Build`; other platforms provide their own implementation.
 */
interface PlatformInfoProvider {

    /** Human-readable app version name (e.g. "1.47.0"); empty string if unavailable. */
    fun appVersionName(): String

    /** Monotonic app version code (e.g. 48). */
    fun appVersionCode(): Long

    /**
     * Device model identifier for crash / diagnostics context. On Android this is the
     * bare `Build.MODEL`.
     *
     * TODO(tech-debt): device-model granularity — `Build.MODEL` is not strictly
     * equivalent to iOS `UIDevice.current.model`. Adequate for crash context; revisit
     * normalization when the iOS implementation lands.
     */
    fun deviceModel(): String
}
