// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

/**
 * iOS [PlatformInfoProvider]: reads the main bundle info dictionary and `UIDevice.model`.
 * Compile-verified only until the iOS app target exists.
 */
actual class PlatformInfoProvider {

    actual fun appVersionName(): String =
        NSBundle.mainBundle.objectForInfoDictionaryKey(KEY_VERSION_NAME) as? String ?: ""

    /**
     * `CFBundleVersion` is a plain build number or a dotted version; dotted forms with 2–3
     * components in 0..999 pack as `major * 10^6 + minor * 10^3 + patch`, anything else to 0.
     */
    actual fun appVersionCode(): Long {
        val raw = NSBundle.mainBundle.objectForInfoDictionaryKey(KEY_VERSION_CODE) as? String
            ?: return 0L
        return raw.toLongOrNull() ?: packDottedVersion(raw) ?: 0L
    }

    actual fun deviceModel(): String = UIDevice.currentDevice.model

    private fun packDottedVersion(raw: String): Long? {
        val parts = raw.split('.')
        if (parts.size !in MIN_DOTTED_PARTS..MAX_DOTTED_PARTS) return null
        val nums = parts.map { part ->
            part.toLongOrNull()?.takeIf { it in 0..MAX_DOTTED_COMPONENT } ?: return null
        }
        val patch = nums.getOrElse(PATCH_INDEX) { 0L }
        return nums[MAJOR_INDEX] * MAJOR_SHIFT + nums[MINOR_INDEX] * MINOR_SHIFT + patch
    }

    private companion object {

        private const val KEY_VERSION_NAME = "CFBundleShortVersionString"
        private const val KEY_VERSION_CODE = "CFBundleVersion"

        private const val MIN_DOTTED_PARTS = 2
        private const val MAX_DOTTED_PARTS = 3
        private const val MAJOR_INDEX = 0
        private const val MINOR_INDEX = 1
        private const val PATCH_INDEX = 2

        // Each dotted component occupies three decimal digits of the packed Long.
        private const val MAX_DOTTED_COMPONENT = 999L
        private const val MINOR_SHIFT = 1_000L
        private const val MAJOR_SHIFT = MINOR_SHIFT * MINOR_SHIFT
    }
}
