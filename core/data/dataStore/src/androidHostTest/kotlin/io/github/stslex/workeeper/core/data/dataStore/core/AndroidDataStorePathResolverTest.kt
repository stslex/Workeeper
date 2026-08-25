// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.dataStore.core

import android.app.Application
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File

/**
 * Pins [AndroidDataStorePathResolver] to `filesDir/datastore/<name>.preferences_pb` for every
 * shipped store name; a hand-rebuilt path would silently read an empty store instead of crashing.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = Application::class, sdk = [33])
internal class AndroidDataStorePathResolverTest {

    private lateinit var context: Application
    private lateinit var resolver: AndroidDataStorePathResolver

    // GUARD: acquire the context here, never in a property initializer — Robolectric's sandbox is
    // not installed around test-instance construction.
    @BeforeEach
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resolver = AndroidDataStorePathResolver(context)
    }

    @Test
    fun `every shipped store name resolves under filesDir slash datastore`() {
        SHIPPED_STORE_NAMES.forEach { name ->
            assertEquals(
                File(context.filesDir, "datastore/$name.preferences_pb").absolutePath,
                resolver.resolve(name).toString(),
                "resolved path for $name",
            )
        }
    }

    @Test
    fun `the resolver agrees with the datastore library extension`() {
        SHIPPED_STORE_NAMES.forEach { name ->
            assertEquals(
                context.preferencesDataStoreFile(name).absolutePath,
                resolver.resolve(name).toString(),
                "resolved path for $name",
            )
        }
    }

    private companion object {

        /** Every Preferences file this app owns; spelled out so a new holder must add its name. */
        val SHIPPED_STORE_NAMES = listOf(
            "common_prefs",
            "backup_account_prefs",
            "backup_scheduling_prefs",
            "restore_state_prefs",
            "app_dialogs_prefs",
        )
    }
}
