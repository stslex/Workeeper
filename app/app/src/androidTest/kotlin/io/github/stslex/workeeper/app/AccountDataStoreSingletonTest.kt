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
 * `AccountDataStore` singleton invariant: two `AppGraph`s in one process resolve ONE store over
 * `backup_account_prefs`. GUARD: collect from both graphs — the collision surfaces when the file
 * opens. See documentation/tech-debt.md -> "DataStore singleton bypass".
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
