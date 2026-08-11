// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope

/**
 * Metro-owned via `@ContributesBinding(AppScope)` (auto-aggregated by the app-scope AppGraph).
 * `@SingleIn(AppScope)` = process-lifetime single-owner. `public` for cross-module aggregation
 * (the merged AppGraph in :app:app cannot extend an internal contribution; never hand-construct,
 * resolve via DI). Context is PLAIN: Metro resolves it from the graph's create(applicationContext).
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidPlatformInfoProvider(
    private val context: Context,
) : PlatformInfoProvider {

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

    override fun appVersionName(): String = packageInfo.versionName.orEmpty()

    override fun appVersionCode(): Long = packageInfo.longVersionCode

    override fun deviceModel(): String = Build.MODEL
}
