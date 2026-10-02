// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.transport

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * wear-live-sync.md §7.1, §10.1: the change listener answers the one change path exactly, never a
 * prefix. The test does not spell the Data Layer package: it uses
 * [PhoneChangeListenerService.MESSAGE_ACTION] and [WearProtocol] constants (§8).
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class PhoneChangeListenerManifestTest {

    @Test
    fun `the listener resolves for the exact change path and for no longer or other one`() {
        assertEquals(listOf(SERVICE), servicesFor("wear://any${WearProtocol.CHANGED_PATH}"))
        assertEquals(emptyList<String>(), servicesFor("wear://any${WearProtocol.CHANGED_PATH}x"))
        assertEquals(emptyList<String>(), servicesFor("wear://any${WearProtocol.RPC_PATH}"))
        assertEquals(emptyList<String>(), servicesFor("wear://any/workeeper/wear/v1/other"))
    }

    private fun servicesFor(data: String): List<String> {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(PhoneChangeListenerService.MESSAGE_ACTION)
            .setData(Uri.parse(data))
            .setPackage(context.packageName)
        return context.packageManager
            .queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))
            .map { it.serviceInfo.name }
    }

    private companion object {
        val SERVICE: String = PhoneChangeListenerService::class.java.name
    }
}
