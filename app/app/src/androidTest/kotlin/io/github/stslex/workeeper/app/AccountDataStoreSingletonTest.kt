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
 * The `AccountDataStore` singleton invariant: two `AppGraph`s in one process resolve ONE
 * `DataStore` over `backup_account_prefs`.
 *
 * A `DataStore` is a per-file singleton; `DataStoreProvider` enforces that with a static,
 * process-lifetime map, while `AccountDataStoreImpl` is `@SingleIn(AppScope)` — graph-lifetime.
 * A holder minting its own store per instance collides the moment a second graph exists in the
 * process — exactly what `MetroTestRule`'s per-test graph rebuild produces — and DataStore 1.1+
 * throws `IllegalStateException: There are multiple DataStores active for the same file` on the
 * second collection.
 *
 * [twoAppGraphsInOneProcessShareTheAccountDataStore] **collects from both graphs rather than
 * merely constructing them**: the collision manifests only when the file opens, so a test that
 * stopped at graph construction (or at resolving `backupAuth`) would stay green even with a
 * per-instance store present.
 *
 * [accountPrefsFileIsTheRelativePathUnderFilesDir] pins the prefs-name → file mapping the
 * provider routing relies on. The pin is the RELATIVE path under `filesDir` on purpose — the
 * absolute path contains the applicationId, which differs between `:app:dev` and `:app:store`.
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
    fun accountPrefsFileIsTheRelativePathUnderFilesDir() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            File(ctx.filesDir, "datastore/backup_account_prefs.preferences_pb"),
            ctx.preferencesDataStoreFile("backup_account_prefs"),
        )
    }
}
