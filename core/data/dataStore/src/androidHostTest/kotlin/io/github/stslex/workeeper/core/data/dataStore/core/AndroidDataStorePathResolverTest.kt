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
 * The storage contract of the KMP split: moving the store surface into `commonMain` must not move a
 * single byte on disk.
 *
 * Before the split, every holder called `Context.preferencesDataStoreFile(name)` directly. Now
 * [AndroidDataStorePathResolver] owns that call on Android's behalf, so this pins its output to the
 * path that function produces — `filesDir/datastore/<name>.preferences_pb` — for every store name
 * this repo actually ships.
 *
 * This is not a tautology despite the implementation delegating to the same function: it fails the
 * moment anyone rebuilds the path by hand (a dropped `datastore/` segment, a changed suffix,
 * `cacheDir` instead of `filesDir`), which is the realistic way this breaks. That failure would
 * otherwise be silent — a resolver pointing somewhere new does not crash, it just reads an empty
 * store, and the user's settings appear to have reset themselves.
 *
 * The expectation is written out as a literal relative path rather than by calling the library
 * function twice, so the test states what the contract IS instead of asserting a function equals
 * itself.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = Application::class, sdk = [33])
internal class AndroidDataStorePathResolverTest {

    private lateinit var context: Application
    private lateinit var resolver: AndroidDataStorePathResolver

    /**
     * Acquired here, never in a property initializer. Under the tech.apter JUnit 5 bridge
     * Robolectric's sandbox is installed around `@BeforeEach` and `@Test`, but NOT around
     * construction of the test instance, so `ApplicationProvider.getApplicationContext()` in a
     * property initializer dies with `IllegalStateException: No instrumentation registered!` — a
     * failure that looks like a broken subject rather than a misplaced call.
     */
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

        /**
         * Every Preferences file this app owns. Sourced from the four holders that mint one:
         * `CommonDataStoreImpl`, `AccountDataStoreImpl`, `BackupPreferencesRepositoryImpl`,
         * `RestoreStateRepositoryImpl` and `AppDialogRepository`. A name added here without a
         * matching holder is harmless; a holder added without its name here loses this pin, which is
         * why the list is spelled out rather than derived.
         */
        val SHIPPED_STORE_NAMES = listOf(
            "common_prefs",
            "backup_account_prefs",
            "backup_scheduling_prefs",
            "restore_state_prefs",
            "app_dialogs_prefs",
        )
    }
}
