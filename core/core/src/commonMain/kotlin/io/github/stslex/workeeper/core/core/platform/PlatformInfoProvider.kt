// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

/**
 * Platform/runtime information (app version, device model) without platform SDK types in the
 * domain layer. An expect/actual class: consumers inject it directly, with no DI binding.
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
