// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.transport

import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.wear.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * wear-live-sync.md §5.1, §10.1: the watch advertises exactly the capability the phone looks up to
 * signal it. The phone's capability is pinned the same way by `WearRpcManifestTest`.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class WatchCapabilityTest {

    @Test
    fun `the advertised capability is the one the phone signals`() {
        val capabilities = RuntimeEnvironment.getApplication()
            .resources
            .getStringArray(R.array.android_wear_capabilities)

        assertEquals(listOf(WearProtocol.WATCH_CAPABILITY), capabilities.toList())
    }
}
