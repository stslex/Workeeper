// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.snapshot

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File
import java.io.IOException

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class SnapshotFileOperationsTest {

    private lateinit var context: Context
    private lateinit var databaseRoot: File
    private lateinit var databaseContext: Context

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        databaseRoot = File(context.cacheDir, "live-replacement-durability").apply {
            deleteRecursively()
            assertTrue(mkdirs())
        }
        databaseContext = object : ContextWrapper(context) {
            override fun getDatabasePath(name: String): File = File(databaseRoot, name)
        }
    }

    @AfterEach
    fun teardown() {
        databaseRoot.deleteRecursively()
    }

    @Test
    fun `live replacement syncs complete temporary and parent around atomic publication`() {
        val source = File(databaseRoot, "live-replacement-source.db").apply {
            writeText("NEW")
        }
        val target = databaseContext.getDatabasePath(AppDatabase.NAME).apply {
            writeText("OLD")
        }
        val temporary = File(databaseRoot, "${AppDatabase.NAME}.tmp")
        var temporarySynced = false
        var directorySyncCount = 0
        val durability = object : SnapshotFileDurability {
            override fun syncFile(file: File) {
                assertEquals(temporary.canonicalFile, file.canonicalFile)
                assertEquals("NEW", file.readText())
                assertEquals("OLD", target.readText())
                temporarySynced = true
            }

            override fun syncDirectory(directory: File) {
                assertEquals(databaseRoot.canonicalFile, directory.canonicalFile)
                assertTrue(temporarySynced)
                directorySyncCount += 1
                if (directorySyncCount == 1) {
                    assertEquals("OLD", target.readText())
                    assertEquals("NEW", temporary.readText())
                } else {
                    assertEquals("NEW", target.readText())
                    assertFalse(temporary.exists())
                }
            }
        }

        val result = LiveDatabaseFileReplacer.replace(databaseContext, source, durability)

        assertTrue(result is BackupResult.Success)
        assertEquals(2, directorySyncCount)
        assertEquals("NEW", target.readText())
    }

    @Test
    fun `pre-publication directory sync failure leaves old live database intact`() {
        val source = File(databaseRoot, "live-replacement-pre-sync-failure.db").apply {
            writeText("NEW")
        }
        val target = databaseContext.getDatabasePath(AppDatabase.NAME).apply {
            writeText("OLD")
        }
        val durability = object : SnapshotFileDurability {
            override fun syncFile(file: File) = Unit

            override fun syncDirectory(directory: File) {
                throw IOException("pre-publication directory fsync failed")
            }
        }

        val result = LiveDatabaseFileReplacer.replace(databaseContext, source, durability)

        assertTrue(result is BackupResult.Failure && result.error is BackupError.Io)
        assertEquals("OLD", target.readText())
        assertFalse(File(databaseRoot, "${AppDatabase.NAME}.tmp").exists())
    }

    @Test
    fun `undeletable stale sidecar rejects before publishing the new live database`() {
        val source = File(databaseRoot, "live-replacement-sidecar-failure.db").apply {
            writeText("NEW")
        }
        val target = databaseContext.getDatabasePath(AppDatabase.NAME).apply {
            writeText("OLD")
        }
        val staleWal = File(databaseRoot, "${AppDatabase.NAME}-wal").apply {
            assertTrue(mkdir())
            File(this, "undeletable-entry").writeText("STALE")
        }

        val result = LiveDatabaseFileReplacer.replace(databaseContext, source)

        assertTrue(result is BackupResult.Failure && result.error is BackupError.Io)
        assertEquals("OLD", target.readText())
        assertTrue(staleWal.isDirectory)
        assertFalse(File(databaseRoot, "${AppDatabase.NAME}.tmp").exists())
    }

    @Test
    fun `post-publication directory sync failure cannot report replacement success`() {
        val source = File(databaseRoot, "live-replacement-post-sync-failure.db").apply {
            writeText("NEW")
        }
        val target = databaseContext.getDatabasePath(AppDatabase.NAME).apply {
            writeText("OLD")
        }
        var directorySyncCount = 0
        val durability = object : SnapshotFileDurability {
            override fun syncFile(file: File) = Unit

            override fun syncDirectory(directory: File) {
                directorySyncCount += 1
                if (directorySyncCount == 2) {
                    throw IOException("published directory fsync failed")
                }
            }
        }

        val result = LiveDatabaseFileReplacer.replace(databaseContext, source, durability)

        assertTrue(result is BackupResult.Failure && result.error is BackupError.Io)
        assertEquals(2, directorySyncCount)
        assertEquals("NEW", target.readText())
    }
}
