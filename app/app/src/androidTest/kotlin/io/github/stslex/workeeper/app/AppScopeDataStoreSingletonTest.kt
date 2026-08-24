// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.data.database_test.InMemoryDatabaseProvider
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.core.ui.test.fakes.FakeImageStorage
import io.github.stslex.workeeper.di.AppGraph
import io.github.stslex.workeeper.di.buildAppGraph
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DataStore singleton invariant: two `AppGraph`s in one process must resolve ONE store per prefs
 * file. GUARD: every test must READ from both graphs — the collision surfaces only when the file
 * opens. See documentation/tech-debt.md -> "DataStore singleton bypass".
 */
@Regression
@RunWith(AndroidJUnit4::class)
internal class AppScopeDataStoreSingletonTest {

    @Test
    fun twoAppGraphsInOneProcessShareTheBackupPreferencesDataStore() {
        assertReadableFromTwoGraphs { graph ->
            graph.backupPreferencesRepository.observe().first()
        }
    }

    @Test
    fun twoAppGraphsInOneProcessShareTheRestoreStateDataStore() {
        assertReadableFromTwoGraphs { graph ->
            graph.restoreStateRepository.observePreRestoreBackupAvailable().first()
        }
    }

    @Test
    fun twoAppGraphsInOneProcessShareTheAppDialogDataStore() {
        assertReadableFromTwoGraphs { graph ->
            graph.appDialogRepository.currentDialog.first()
        }
    }

    @Test
    fun backupSchedulingPrefsFileIsTheRelativePathUnderFilesDir() {
        assertPrefsFile("backup_scheduling_prefs")
    }

    @Test
    fun restoreStatePrefsFileIsTheRelativePathUnderFilesDir() {
        assertPrefsFile("restore_state_prefs")
    }

    @Test
    fun appDialogsPrefsFileIsTheRelativePathUnderFilesDir() {
        assertPrefsFile("app_dialogs_prefs")
    }

    /** Two graphs, one process: the load-bearing part is that the SECOND read completes at all. */
    private fun <T> assertReadableFromTwoGraphs(read: suspend (AppGraph) -> T) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val firstDatabase = InMemoryDatabaseProvider.create(context)
        val secondDatabase = InMemoryDatabaseProvider.create(context)
        try {
            val firstGraph = buildAppGraph(
                applicationContext = context,
                appDatabase = firstDatabase,
                imageStorage = FakeImageStorage(),
            )
            val secondGraph = buildAppGraph(
                applicationContext = context,
                appDatabase = secondDatabase,
                imageStorage = FakeImageStorage(),
            )
            runBlocking {
                val first = read(firstGraph)
                val second = read(secondGraph)
                assertEquals(first, second)
            }
        } finally {
            firstDatabase.close()
            secondDatabase.close()
        }
    }

    private fun assertPrefsFile(prefsName: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            File(context.filesDir, "datastore/$prefsName.preferences_pb"),
            context.preferencesDataStoreFile(prefsName),
        )
    }
}
