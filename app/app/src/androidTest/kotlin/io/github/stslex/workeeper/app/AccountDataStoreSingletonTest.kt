// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.data.database_test.InMemoryDatabaseProvider
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.core.ui.test.fakes.FakeImageStorage
import io.github.stslex.workeeper.di.buildAppGraph
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression cover for the `AccountDataStoreImpl` singleton bypass (nav3 stage 1.1).
 *
 * A `DataStore` is a per-file singleton; `DataStoreProvider` enforces that with a static,
 * process-lifetime map. `AccountDataStoreImpl` used to build its own store with a per-instance
 * `by lazy { PreferenceDataStoreFactory.create { ... } }`, bypassing that map — so any second
 * `AppGraph` in one process (exactly what `MetroTestRule` does per test) opened a second `DataStore`
 * over `backup_account_prefs` and DataStore 1.1+ threw
 * `IllegalStateException: There are multiple DataStores active for the same file`.
 * That is how `RouteReachabilityTest.archiveOpensFromSettingsAndSettingsReturns` was red on this
 * branch before the fix.
 *
 * [twoAppGraphsInOneProcessShareTheAccountDataStore] rebuilds the failure shape directly: two
 * graphs, one process, both asked for the flag. **The route collects — it does not merely
 * construct.** The store is held in a `by lazy`, so resolving `backupAuth` (or even
 * `AccountDataStoreImpl` itself) touches no file; a test that stopped there would stay green with
 * the defect present. `first()` on the cold `observeDriveFileGranted()` flow forces the file open
 * on both graphs, which is the collision. Proven red against the unfixed code before the fix was
 * applied, then green after — in that order.
 *
 * [accountPrefsFileIsTheRelativePathTheFixPreserves] pins the file identity the fix relies on:
 * routing through `DataStoreProvider` is only a no-op for existing users because both sides resolve
 * `context.preferencesDataStoreFile("backup_account_prefs")`. The pin is the RELATIVE path under
 * `filesDir` on purpose — the absolute path contains the applicationId, which differs between
 * `:app:dev` and `:app:store`.
 */
@Regression
@RunWith(AndroidJUnit4::class)
internal class AccountDataStoreSingletonTest {

    @Test
    fun twoAppGraphsInOneProcessShareTheAccountDataStore() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db1 = InMemoryDatabaseProvider.create(ctx)
        val db2 = InMemoryDatabaseProvider.create(ctx)
        try {
            val graph1 = buildAppGraph(
                applicationContext = ctx,
                appDatabase = db1,
                imageStorage = FakeImageStorage(),
            )
            val graph2 = buildAppGraph(
                applicationContext = ctx,
                appDatabase = db2,
                imageStorage = FakeImageStorage(),
            )
            runBlocking {
                val first = graph1.backupAuth.observeDriveFileGranted().first()
                val second = graph2.backupAuth.observeDriveFileGranted().first()
                assertEquals(first, second)
            }
        } finally {
            db1.close()
            db2.close()
        }
    }

    @Test
    fun accountPrefsFileIsTheRelativePathTheFixPreserves() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            File(ctx.filesDir, "datastore/backup_account_prefs.preferences_pb"),
            ctx.preferencesDataStoreFile("backup_account_prefs"),
        )
    }
}
