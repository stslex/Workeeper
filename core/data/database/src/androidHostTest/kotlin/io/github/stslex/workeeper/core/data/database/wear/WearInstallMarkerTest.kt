// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.wear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File
import kotlin.uuid.Uuid

/**
 * The three install states the platform can hand a launch. Auto Backup copies `databases/`
 * verbatim, so "an epoch already exists" says nothing about whose install created it — only the
 * marker under `noBackupFilesDir`, which backup never captures, can tell them apart.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class WearInstallMarkerTest : BaseDatabaseTest() {

    private lateinit var markerDirectory: File
    private lateinit var marker: WearInstallMarker

    @BeforeEach
    fun setup() {
        initDb()
        val context: Context = ApplicationProvider.getApplicationContext()
        markerDirectory = File(context.noBackupFilesDir, "marker-${Uuid.random()}")
        marker = WearInstallMarker(markerDirectory)
    }

    @AfterEach
    fun teardown() {
        clearDb()
        markerDirectory.deleteRecursively()
    }

    @Test
    fun `marker present - this install owns the file, so nothing rotates`() = runTest {
        val epoch = prepareWearSyncStorage(database, rotateDatabaseEpoch = false)
        val session = insertActiveSession()
        val receipt = storeReceipt(session, epoch)
        marker.recordInstall()

        val launched = prepareWearSyncStorageForLaunch(database, marker, rotateDatabaseEpoch = false)

        assertEquals(epoch, launched, "an install that already prepared this file must not rotate")
        val after = requireNotNull(database.wearSyncDao.getSessionSync(session))
        assertEquals(receipt, after.receiptCommandId)
        assertEquals(epoch, after.receiptDatabaseEpoch)
    }

    @Test
    fun `marker absent over an existing epoch - a restored file rotates and drops its receipts`() =
        runTest {
            // Exactly what Auto Backup or device transfer produces: a seeded wear_database_metadata
            // row and stale receipts, in an install that has never prepared this file itself.
            val foreignEpoch = prepareWearSyncStorage(database, rotateDatabaseEpoch = false)
            val session = insertActiveSession()
            storeReceipt(session, foreignEpoch)

            val launched = prepareWearSyncStorageForLaunch(
                database,
                marker,
                rotateDatabaseEpoch = false,
            )

            assertNotEquals(foreignEpoch, launched, "a restored file must not keep a foreign epoch")
            Uuid.parse(launched)
            val after = requireNotNull(database.wearSyncDao.getSessionSync(session))
            assertNull(after.receiptCommandId)
            assertNull(after.receiptAttemptFingerprint)
            assertNull(after.receiptDatabaseEpoch)
            assertNull(after.receiptRevision)
            assertMarkerWritten("the marker closes this launch's rotation")
        }

    @Test
    fun `fresh install - the epoch is seeded and the marker is written`() = runTest {
        val launched = prepareWearSyncStorageForLaunch(database, marker, rotateDatabaseEpoch = false)

        Uuid.parse(launched)
        assertEquals(
            launched,
            requireNotNull(database.wearSyncDao.getDatabaseMetadata()).databaseEpoch,
        )
        assertMarkerWritten("a fresh install records itself")
        // Second launch of the same install: the marker is now present, so the epoch is stable.
        assertEquals(
            launched,
            prepareWearSyncStorageForLaunch(database, marker, rotateDatabaseEpoch = false),
        )
    }

    @Test
    fun `the production marker lives in the directory backup cannot capture`() {
        val context: Context = ApplicationProvider.getApplicationContext()

        wearInstallMarker(context).recordInstall()

        assertTrue(
            context.noBackupFilesDir.listFiles().orEmpty().any { it.isFile },
            "the install marker must land under noBackupFilesDir",
        )
        context.noBackupFilesDir.listFiles().orEmpty().forEach(File::delete)
    }

    /** Observed as a file, not through [WearInstallMarker.isInstallRecorded] — a broken check
     * must not be able to answer for its own effect. */
    private fun assertMarkerWritten(message: String) = assertTrue(
        markerDirectory.listFiles().orEmpty().any(File::isFile),
        message,
    )

    private suspend fun insertActiveSession(): Uuid {
        val training = TrainingEntity(
            name = "Strength",
            description = null,
            isAdhoc = false,
            archived = false,
            createdAt = 1,
            archivedAt = null,
        )
        database.trainingDao.insert(training)
        val session = SessionEntity(
            trainingUuid = training.uuid,
            state = SessionStateEntity.IN_PROGRESS,
            startedAt = 1,
            finishedAt = null,
        )
        database.sessionDao.insert(session)
        return session.uuid
    }

    private suspend fun storeReceipt(sessionUuid: Uuid, epoch: String): String {
        val commandId = Uuid.random().toString()
        val current = requireNotNull(database.wearSyncDao.getSessionSync(sessionUuid))
        assertEquals(
            1,
            database.wearSyncDao.storeReceipt(
                sessionUuid = sessionUuid,
                commandId = commandId,
                attemptFingerprint = ByteArray(FINGERPRINT_SIZE_BYTES) { 0x5a },
                databaseEpoch = epoch,
                revision = current.revision,
            ),
        )
        return commandId
    }

    private companion object {
        const val FINGERPRINT_SIZE_BYTES = 34
    }
}
