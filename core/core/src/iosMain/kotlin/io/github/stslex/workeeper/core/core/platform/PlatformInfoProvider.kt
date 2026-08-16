// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

/**
 * iOS [PlatformInfoProvider]: reads the main bundle's info dictionary
 * (`CFBundleShortVersionString` / `CFBundleVersion`) and `UIDevice.model`.
 * Compile-verified only until the iOS app target exists — no runtime consumer yet.
 */
actual class PlatformInfoProvider {

    actual fun appVersionName(): String =
        NSBundle.mainBundle.objectForInfoDictionaryKey(KEY_VERSION_NAME) as? String ?: ""

    actual fun appVersionCode(): Long =
        (NSBundle.mainBundle.objectForInfoDictionaryKey(KEY_VERSION_CODE) as? String)
            ?.toLongOrNull()
            ?: 0L

    actual fun deviceModel(): String = UIDevice.currentDevice.model

    private companion object {

        private const val KEY_VERSION_NAME = "CFBundleShortVersionString"
        private const val KEY_VERSION_CODE = "CFBundleVersion"
    }
}
