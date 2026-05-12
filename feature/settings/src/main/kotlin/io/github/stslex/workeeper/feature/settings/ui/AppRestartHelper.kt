// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * Re-launches the app from a fresh process by clearing the task stack, finishing the
 * current activity affinity, and terminating the JVM. Destructive — only valid in the
 * narrow window after `DatabaseSnapshotProvider.restoreFromSnapshot` swaps the live
 * Room database file, because the in-process DAO graph is then stale and only a
 * cold start can rebuild it.
 *
 * Lives in `feature/settings` until a second caller justifies promotion to
 * `core/ui/kit`.
 */
internal fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: error("No launch intent for package ${context.packageName}")
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    context.startActivity(intent)
    if (context is Activity) context.finishAffinity()
    Runtime.getRuntime().exit(0)
}
