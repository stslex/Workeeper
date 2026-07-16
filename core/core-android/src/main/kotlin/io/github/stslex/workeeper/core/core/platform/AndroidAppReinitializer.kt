// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

import android.content.Context
import android.content.Intent
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope

/**
 * Android [AppReinitializer]: a full process restart. Relaunches the app's launcher
 * activity in a fresh task and terminates the current process, so the OS tears down
 * every Activity and the next process rebuilds the DI graph and reopens Room against
 * the already-swapped database file.
 *
 * This is the single, consolidated restart implementation — it replaces the two
 * previously byte-identical bodies in `RestoreRecoveryCoordinator` and `NavigatorExt`.
 * It runs on the application [Context], so it never holds an `Activity`:
 * `FLAG_ACTIVITY_CLEAR_TASK` finishes the old task's activities and `Runtime.exit`
 * terminates the process, which supersedes any per-Activity `finishAffinity` teardown
 * the Activity-context call site used to do.
 */
/**
 * Metro-owned via `@ContributesBinding(AppScope)` (auto-aggregated by the app-scope
 * AppGraph). `@SingleIn(AppScope)` = process-lifetime single-owner. `public` for cross-module aggregation
 * (the merged AppGraph in :app:app cannot extend an internal contribution; never hand-construct,
 * resolve via DI). Context is PLAIN (Metro resolves it from the graph's create(applicationContext)).
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidAppReinitializer(
    private val context: Context,
) : AppReinitializer {

    override fun reinitialize() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: error("No launch intent for package ${context.packageName}")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
