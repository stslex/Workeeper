// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.transport

import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.wear.runtime.WatchRuntimeFactory

/**
 * The watch end of the change signal (wear-live-sync.md §5, §7.1): Google Play services delivers the
 * phone's [WearProtocol.CHANGED_PATH] message here, with the app closed too, starting the process if
 * it must, and the runtime asks the phone again (origin O6). The message carries no workout data;
 * its payload is ignored.
 *
 * GUARD: one of the files allowed to name the Wearable Data Layer (wear-live-sync.md §8); widening
 * that allowlist is a privacy decision. Everything here is plumbing: the O6 rules live in
 * [WatchTransportCoordinator], which is pure Kotlin and host-tested. Debug runtimes keep the
 * default no-op, so no synthetic suite reacts to a signal.
 */
class PhoneChangeListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearProtocol.CHANGED_PATH) return
        // In a cold process this builds the runtime and its Tile observer, as an Activity start does.
        WatchRuntimeFactory.get(applicationContext).onPhoneChanged()
    }

    companion object {
        /** The manifest action for [onMessageReceived]; tests use it instead of spelling the Data Layer package. */
        const val MESSAGE_ACTION: String = MessageClient.ACTION_MESSAGE_RECEIVED
    }
}
