// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge.transport

import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.WearableListenerService
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.feature.wear_bridge.WearBridgeWorkDepsHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.tasks.asTask

/**
 * The phone end of the paired transport: answers the watch's `MessageClient.sendRequest` on
 * [WearProtocol.RPC_PATH] (wear-paired-transport.md §5.3, §6.1).
 *
 * GUARD: one of the two files allowed to name the Wearable Data Layer (§8); widening that
 * allowlist is a privacy decision. Everything here is plumbing: routing, admission and encoding
 * live in [PhoneWearRpcHandler], which is pure Kotlin and host-tested.
 *
 * Google Play services binds this service only for a caller with the same package and signature
 * (the listener stub checks the calling UID), and the manifest filter matches [WearProtocol.RPC_PATH]
 * exactly.
 */
class WearRpcListenerService : WearableListenerService() {

    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        if (path != WearProtocol.RPC_PATH) return null
        val handler = PhoneWearRpcHandler(
            holder = application as? WearBridgeWorkDepsHolder,
            admissionScope = scope,
        )
        return scope.async { handler.handle(nodeId, request) }.asTask()
    }

    companion object {
        /** The manifest action for [onRequest]; tests use it instead of spelling the Data Layer package. */
        const val REQUEST_ACTION: String = MessageClient.ACTION_REQUEST_RECEIVED

        /**
         * Process lifetime, never cancelled in `onDestroy`: Google Play services may unbind the
         * service before a request's `Task` completes, and an admitted bridge call must finish.
         */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
