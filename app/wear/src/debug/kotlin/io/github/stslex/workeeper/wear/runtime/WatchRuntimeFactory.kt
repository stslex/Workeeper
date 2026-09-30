// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import io.github.stslex.workeeper.wear.cache.AtomicFileRecordStorage
import io.github.stslex.workeeper.wear.cache.BootCountProvider
import io.github.stslex.workeeper.wear.cache.ElapsedRealtimeClock
import io.github.stslex.workeeper.wear.ongoing.AndroidOngoingNotification
import io.github.stslex.workeeper.wear.ongoing.OngoingPolicy
import io.github.stslex.workeeper.wear.tile.observeWorkoutTile
import java.io.File

internal object WatchRuntimeFactory {
    private var instance: RuntimeInstance? = null

    @Synchronized
    fun get(context: Context): WatchRuntime = instance(context).owner

    @Synchronized
    fun handleDebugScenario(context: Context, scenario: String): Boolean = instance(context).driver.handle(scenario)

    private fun instance(context: Context): RuntimeInstance = instance ?: create(context.applicationContext).also {
        instance = it
    }

    private fun create(context: Context): RuntimeInstance {
        val clock = DebugElapsedClock(ElapsedRealtimeClock(SystemClock::elapsedRealtime))
        val ids = DebugRuntimeIds(clock.nowMs())
        val owner = WatchRuntimeOwner(
            storage = AtomicFileRecordStorage(File(context.noBackupFilesDir, "synthetic_watch_snapshot")),
            clock = clock,
            bootCount = BootCountProvider {
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1).takeIf { it >= 0 }
            },
            notification = AndroidOngoingNotification(context, clock),
            policy = OngoingPolicy(DEBUG_UNCALIBRATED_RECONNECT_WINDOW_MS),
            identity = RuntimeIdentity(sourceNodeId = "synthetic-watch", ids = ids),
            scheduler = CoroutineDeadlineScheduler(clock),
            selectedLocale = context.resources.configuration.locales[0],
        )
        observeWorkoutTile(context, owner)
        return RuntimeInstance(owner, DebugSnapshotDriver(owner, ids, clock))
    }

    private data class RuntimeInstance(val owner: WatchRuntimeOwner, val driver: DebugSnapshotDriver)
}
