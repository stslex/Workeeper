// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope

/**
 * Android [PlatformInfoProvider]: reads `PackageManager` + `Build`. Consumers inject the class
 * itself; Metro resolves `Context` from the graph's `create(applicationContext)`.
 */
@SingleIn(AppScope::class)
@Inject
actual class PlatformInfoProvider(
    private val context: Context,
) {

    private val packageInfo: PackageInfo by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    }

    actual fun appVersionName(): String = packageInfo.versionName.orEmpty()

    actual fun appVersionCode(): Long = packageInfo.longVersionCode

    actual fun deviceModel(): String = Build.MODEL
}
