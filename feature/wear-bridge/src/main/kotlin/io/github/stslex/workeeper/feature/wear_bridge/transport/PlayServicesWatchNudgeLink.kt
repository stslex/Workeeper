// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge.transport

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import kotlinx.coroutines.tasks.await

/**
 * The phone end of the change signal (wear-live-sync.md §5.2, §6.3): finds the reachable watches that
 * advertise [WearProtocol.WATCH_CAPABILITY] and sends one of them the content-free
 * [WearProtocol.CHANGED_PATH] message. No workout data crosses here; the watch asks for it.
 *
 * GUARD: one of the files allowed to name the Wearable Data Layer (wear-live-sync.md §8); widening
 * that allowlist is a privacy decision. Sending rules live in [PhoneChangeNotifier], which is pure
 * Kotlin and host-tested.
 *
 * GUARD: no Google Play services call before the first signal. The clients are lazy.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PlayServicesWatchNudgeLink @Inject internal constructor(context: Context) : WatchNudgeLink {

    private val appContext = context.applicationContext
    private val capabilities by lazy { Wearable.getCapabilityClient(appContext) }
    private val messages by lazy { Wearable.getMessageClient(appContext) }

    override suspend fun reachableWatches(): List<String> = capabilities
        .getCapability(WearProtocol.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
        .await()
        .nodes
        .map { node -> node.id }

    override suspend fun signal(nodeId: String) {
        // §5.2: one byte 0x00, because discovery left an empty payload (ASM-8) unproven. It carries
        // nothing; the watch ignores it.
        messages.sendMessage(nodeId, WearProtocol.CHANGED_PATH, byteArrayOf(0)).await()
    }
}
