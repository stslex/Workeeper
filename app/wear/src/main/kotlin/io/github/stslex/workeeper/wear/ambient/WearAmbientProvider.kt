// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ambient

import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.wear.ambient.AmbientLifecycleObserver
import kotlinx.coroutines.flow.StateFlow

internal interface WearAmbientProvider {
    val state: StateFlow<WearAmbientState>
}

/**
 * Activity-owned provider. [expireAuthority] checks the existing monotonic deadline synchronously;
 * it must not acquire or renew authority, and must not use the display's wall-clock timestamp.
 */
@MainThread
internal class AndroidWearAmbientProvider(
    activity: ComponentActivity,
    expireAuthority: () -> Unit,
    wallClockMillis: () -> Long = System::currentTimeMillis,
) : WearAmbientProvider {

    private val controller = WearAmbientController(wallClockMillis, expireAuthority)
    override val state = controller.state

    private val observer = AmbientLifecycleObserver(
        activity = activity,
        callbacks = WearAmbientLifecycleCallbacks(controller),
    )

    init {
        activity.lifecycle.addObserver(observer)
    }
}

internal class WearAmbientLifecycleCallbacks(
    private val controller: WearAmbientController,
) : AmbientLifecycleObserver.AmbientLifecycleCallback {

    override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
        controller.onEnterAmbient(
            deviceHasLowBitAmbient = ambientDetails.deviceHasLowBitAmbient,
            burnInProtectionRequired = ambientDetails.burnInProtectionRequired,
        )
    }

    override fun onUpdateAmbient() {
        controller.onSystemUpdate()
    }

    override fun onExitAmbient() {
        controller.onExitAmbient()
    }
}
