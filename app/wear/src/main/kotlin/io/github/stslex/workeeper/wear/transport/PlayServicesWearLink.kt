// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.transport

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import kotlinx.coroutines.tasks.await

/**
 * The watch end of the paired transport: `MessageClient.sendRequest` to the phone's listener on
 * [WearProtocol.RPC_PATH] (wear-paired-transport.md §5.2, §5.3).
 *
 * GUARD: one of the two files allowed to name the Wearable Data Layer (§8); widening that allowlist
 * is a privacy decision. Everything here is plumbing; sending rules live in
 * [WatchTransportCoordinator], which is pure Kotlin and host-tested.
 *
 * GUARD: no Google Play services call before the first request. The clients are lazy, so the
 * release factory constructs under Robolectric and on a watch without Play services, where every
 * request then fails and is classified as unreachable (§7.7).
 */
internal class PlayServicesWearLink(context: Context) : WearLink {

    private val appContext = context.applicationContext
    private val capabilities by lazy { Wearable.getCapabilityClient(appContext) }
    private val messages by lazy { Wearable.getMessageClient(appContext) }
    private val nodes by lazy { Wearable.getNodeClient(appContext) }

    override suspend fun localNodeId(): String = nodes.localNode.await().id

    override suspend fun reachablePhones(): List<PhoneNode> = capabilities
        .getCapability(WearProtocol.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
        .await()
        .nodes
        .map { node -> PhoneNode(id = node.id, isNearby = node.isNearby) }

    override suspend fun request(nodeId: String, request: ByteArray): ByteArray =
        messages.sendRequest(nodeId, WearProtocol.RPC_PATH, request).await() ?: ByteArray(0)

    override suspend fun observeReachability(onChange: (reachable: Boolean) -> Unit) {
        // The Task reports a failed registration; awaiting it lets the caller retry (§7.7).
        capabilities.addListener({ info -> onChange(info.nodes.isNotEmpty()) }, WearProtocol.PHONE_CAPABILITY)
            .await()
    }
}
