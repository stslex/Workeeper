// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge.transport

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.feature.wear_bridge.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * wear-paired-transport.md §10.1: the service answers the one RPC path exactly, and the watch
 * discovers the phone by the capability this module advertises. Neither test spells the Data Layer
 * package: they use [WearRpcListenerService.REQUEST_ACTION] and [WearProtocol] constants (§8).
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class WearRpcManifestTest {

    @Test
    fun `the listener resolves for the exact RPC path and not for a longer one`() {
        assertEquals(listOf(SERVICE), servicesFor("wear://any${WearProtocol.RPC_PATH}"))
        assertEquals(emptyList<String>(), servicesFor("wear://any${WearProtocol.RPC_PATH}x"))
        assertEquals(emptyList<String>(), servicesFor("wear://any/workeeper/wear/v1/other"))
    }

    @Test
    fun `the advertised capability is the one the watch discovers`() {
        val capabilities = RuntimeEnvironment.getApplication()
            .resources
            .getStringArray(R.array.android_wear_capabilities)

        assertEquals(listOf(WearProtocol.PHONE_CAPABILITY), capabilities.toList())
    }

    @Test
    fun `the listener answers no other path`() {
        val service = WearRpcListenerService()

        assertNull(service.onRequest(NODE, "/workeeper/wear/v1/other", ByteArray(0)))
        assertNull(service.onRequest(NODE, "${WearProtocol.RPC_PATH}x", ByteArray(0)))
    }

    private fun servicesFor(data: String): List<String> {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(WearRpcListenerService.REQUEST_ACTION)
            .setData(Uri.parse(data))
            .setPackage(context.packageName)
        return context.packageManager
            .queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))
            .map { it.serviceInfo.name }
    }

    private companion object {
        val SERVICE: String = WearRpcListenerService::class.java.name
        const val NODE = "watch-node"
    }
}
