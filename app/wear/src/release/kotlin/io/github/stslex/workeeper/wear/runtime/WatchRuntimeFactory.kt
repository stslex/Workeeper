// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.wear.cache.AtomicFileRecordStorage
import io.github.stslex.workeeper.wear.cache.BootCountProvider
import io.github.stslex.workeeper.wear.cache.ElapsedRealtimeClock
import io.github.stslex.workeeper.wear.ongoing.AndroidOngoingNotification
import io.github.stslex.workeeper.wear.ongoing.OngoingPolicy
import io.github.stslex.workeeper.wear.tile.observeWorkoutTile
import io.github.stslex.workeeper.wear.transport.LocalNodeId
import io.github.stslex.workeeper.wear.transport.PlayServicesWearLink
import io.github.stslex.workeeper.wear.transport.WatchTransportCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * The release runtime: the owner-backed runtime connected to the phone through the paired
 * transport (wear-paired-transport.md §7.7), built once per process. Neither this factory nor the
 * link calls Google Play services before the first request. No synthetic source is reachable from
 * release, and [handleDebugScenario] always refuses.
 */
internal object WatchRuntimeFactory {
    private var runtime: ConnectedWatchRuntime? = null

    @Synchronized
    fun get(context: Context): WatchRuntime = runtime ?: create(context.applicationContext).also {
        runtime = it
    }

    fun handleDebugScenario(context: Context, scenario: String): Boolean = false

    private fun create(context: Context): ConnectedWatchRuntime {
        val clock = ElapsedRealtimeClock(SystemClock::elapsedRealtime)
        val localNode = LocalNodeId()
        val owner = WatchRuntimeOwner(
            storage = AtomicFileRecordStorage(File(context.noBackupFilesDir, "watch_snapshot")),
            clock = clock,
            bootCount = BootCountProvider {
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1).takeIf { it >= 0 }
            },
            notification = AndroidOngoingNotification(context, clock),
            policy = OngoingPolicy(RELEASE_PROVISIONAL_RECONNECT_WINDOW_MS),
            identity = RuntimeIdentity(nodeId = localNode::value, ids = { CanonicalUuid.random() }),
            scheduler = CoroutineDeadlineScheduler(clock),
            selectedLocale = context.resources.configuration.locales[0],
        )
        val transport = WatchTransportCoordinator(
            owner = owner,
            link = PlayServicesWearLink(context),
            localNode = localNode,
            // GUARD: single-threaded; the coordinator's bookkeeping relies on it.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1)),
            clock = clock,
        )
        return ConnectedWatchRuntime(owner, transport).also { observeWorkoutTile(context, it) }
    }
}

/**
 * PROVISIONAL, internal testing only (wear-paired-transport.md §7.8): Phase 1 §8 asks for at least
 * the documented four-minute reconnection interval plus a margin (30 s). Unmeasured; it closes
 * neither the Phase 1 §8 STOP on measured reconnect behavior nor production lifecycle acceptance.
 */
internal const val RELEASE_PROVISIONAL_RECONNECT_WINDOW_MS: Long = 270_000L
