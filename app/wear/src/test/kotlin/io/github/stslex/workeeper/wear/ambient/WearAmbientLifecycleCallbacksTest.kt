// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ambient

import androidx.wear.ambient.AmbientLifecycleObserver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WearAmbientLifecycleCallbacksTest {

    @Test
    fun `platform callbacks preserve independent capabilities and drive the injected state provider`() {
        listOf(false, true).forEach { lowBit ->
            listOf(false, true).forEach { burnIn ->
                var timestamp = 0L
                var expiryChecks = 0
                val controller = WearAmbientController(
                    wallClockMillis = { timestamp },
                    expireAuthority = { expiryChecks++ },
                )
                val provider: WearAmbientProvider = controller
                val callbacks = WearAmbientLifecycleCallbacks(controller)
                callbacks.onEnterAmbient(
                    AmbientLifecycleObserver.AmbientDetails(
                        burnInProtectionRequired = burnIn,
                        deviceHasLowBitAmbient = lowBit,
                    ),
                )
                assertTrue(provider.state.value.isAmbient)
                assertEquals(lowBit, provider.state.value.deviceHasLowBitAmbient)
                assertEquals(burnIn, provider.state.value.burnInProtectionRequired)
                assertEquals(ambientOffset(0L, burnIn), provider.state.value.offset)

                timestamp = 60_000L
                callbacks.onUpdateAmbient()
                assertEquals(timestamp, provider.state.value.timestampMillis)
                assertEquals(ambientOffset(timestamp, burnIn), provider.state.value.offset)
                callbacks.onExitAmbient()
                assertFalse(provider.state.value.isAmbient)
                assertEquals(WearAmbientOffset(), provider.state.value.offset)
                assertEquals(3, expiryChecks)
            }
        }
    }

    @Test
    fun `each ambient entry replaces previous device details`() {
        val controller = WearAmbientController(wallClockMillis = { 0L }, expireAuthority = {})
        val callbacks = WearAmbientLifecycleCallbacks(controller)
        callbacks.onEnterAmbient(AmbientLifecycleObserver.AmbientDetails(true, true))
        callbacks.onExitAmbient()
        callbacks.onEnterAmbient(AmbientLifecycleObserver.AmbientDetails(false, false))
        assertFalse(controller.state.value.deviceHasLowBitAmbient)
        assertFalse(controller.state.value.burnInProtectionRequired)
        assertEquals(WearAmbientOffset(), controller.state.value.offset)
    }
}
