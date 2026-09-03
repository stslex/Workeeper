// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

import android.content.Context
import android.content.Intent
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope

/**
 * Android [AppReinitializer]: relaunches the launcher activity in a fresh task and exits the
 * process, so the next process rebuilds the DI graph against the already-swapped database file.
 */
@SingleIn(AppScope::class)
@Inject
actual class AppReinitializer(
    private val context: Context,
) {

    actual fun reinitialize() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: error("No launch intent for package ${context.packageName}")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
