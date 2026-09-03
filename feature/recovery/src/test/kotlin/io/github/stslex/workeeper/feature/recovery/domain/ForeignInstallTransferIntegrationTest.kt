// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.domain

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.core.platform.AppReinitializer
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacement
import io.github.stslex.workeeper.core.data.backup.api.restore.ActiveUndo
import io.github.stslex.workeeper.core.data.backup.api.restore.ActiveUndoTransition
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreGarbageCollectionReport
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolRead
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolState
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreSourceRef
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreTerminal
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.scheduling.RestoreStateRepositoryImpl
import io.github.stslex.workeeper.core.data.dataStore.core.AndroidDataStorePathResolver
import io.github.stslex.workeeper.core.data.dataStore.core.DataStoreProvider
import io.github.stslex.workeeper.core.data.dataStore.core.DataStoreProviderFactory
import io.github.stslex.workeeper.core.data.database.migration.APP_DATABASE_VERSION
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.core.data.database.snapshot.RestoreRecoveryFilesImpl
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.recovery.diagnostics.RestoreRecoveryReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File
import java.util.UUID
import java.nio.file.Path as NioPath

/** Cross-install transfer proof at the real DataStore/noBackup/startup-preflight boundary. */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
internal class ForeignInstallTransferIntegrationTest {

    @TempDir
    lateinit var tempDir: NioPath

    @Test
    fun `foreign transferred protocol is cleared and healthy B starts normally byte exact`() =
        runTest {
            val base = ApplicationProvider.getApplicationContext<Context>()
            val installA = InstallContext(base, tempDir.resolve("install-a").toFile())
            val installB = InstallContext(base, tempDir.resolve("install-b").toFile())
            val prefsA = installA.restorePreferencesFile()
            val prefsB = installB.restorePreferencesFile()
            val dialogsA = installA.appDialogPreferencesFile()
            val dialogsB = installB.appDialogPreferencesFile()
            val scopeA = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val scopeB = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val filesA = RestoreRecoveryFilesImpl(installA, Dispatchers.IO)
            val filesB = RestoreRecoveryFilesImpl(installB, Dispatchers.IO)

            try {
                val repositoryA = RestoreStateRepositoryImpl(
                    GenerationFactory(prefsA, scopeA, installA),
                    filesA,
                )
                val epochA = filesA.installEpoch()
                val dialogRepositoryA = AppDialogRepository(
                    GenerationFactory(dialogsA, scopeA, installA),
                    filesA,
                )
                dialogRepositoryA.publish(
                    AppDialog.UndoRestoreConfirmation(
                        undoRef = UndoRef(ACTIVE_OWNER),
                        originalDataDateEpochMs = ACTIVE_DATE,
                    ),
                )
                assertTrue(
                    dialogRepositoryA.currentDialog.first() is
                    AppDialog.UndoRestoreConfirmation,
                )
                publishAttemptAssets(filesA, installA, ACTIVE_OWNER)
                publishAttemptAssets(filesA, installA, ATTEMPT_OWNER)

                val activeAttempt = restoreAttempt(ACTIVE_OWNER)
                assertTrue(repositoryA.beginAttempt(activeAttempt))
                assertTrue(repositoryA.recordAttemptCommitted(ACTIVE_OWNER))
                assertTrue(
                    repositoryA.finalizeAttempt(
                        attemptId = ACTIVE_OWNER,
                        activeUndoTransition = ActiveUndoTransition.Replace(
                            ActiveUndo(UndoRef(ACTIVE_OWNER), ACTIVE_DATE),
                        ),
                        terminal = RestoreTerminal.RestoreSucceeded(
                            owner = ACTIVE_OWNER,
                            restoredAtEpochMs = ACTIVE_DATE + 1,
                            previousVersionAvailable = true,
                        ),
                    ),
                )
                assertTrue(repositoryA.acknowledgeTerminal(ACTIVE_OWNER))
                val unresolvedAttempt = restoreAttempt(ATTEMPT_OWNER)
                assertTrue(repositoryA.beginAttempt(unresolvedAttempt))

                val stateA = currentState(repositoryA.readProtocol())
                assertEquals(epochA, stateA.installEpoch)
                assertEquals(UndoRef(ACTIVE_OWNER), stateA.activeUndo?.ref)
                assertEquals(unresolvedAttempt, stateA.attempt)
                assertTrue(filesA.undoFile(UndoRef(ACTIVE_OWNER))!!.exists())
                assertTrue(filesA.undoFile(UndoRef(ATTEMPT_OWNER))!!.exists())
                assertTrue(filesA.restoreSourceFile(RestoreSourceRef(ATTEMPT_OWNER))!!.exists())

                val liveDbB = File(installB.filesDir, "databases/workeeper.db")
                createHealthyDatabase(liveDbB)
                val liveBytesBeforeTransfer = liveDbB.readBytes()
                assertTrue(installB.noBackupFilesDir.listFiles().orEmpty().isEmpty())

                // Simulate process death before transport reads the dedicated DataStore payload.
                scopeA.coroutineContext.job.cancelAndJoin()
                val transferredPayload = prefsA.readBytes()
                val transferredDialogs = dialogsA.readBytes()
                prefsB.parentFile!!.mkdirs()
                prefsA.copyTo(prefsB, overwrite = false)
                dialogsA.copyTo(dialogsB, overwrite = false)
                assertArrayEquals(transferredPayload, prefsB.readBytes())
                assertArrayEquals(transferredDialogs, dialogsB.readBytes())
                assertTrue(
                    installB.noBackupFilesDir.listFiles().orEmpty().isEmpty(),
                    "noBackup recovery assets are intentionally absent from the transfer",
                )

                val repositoryB = RestoreStateRepositoryImpl(
                    GenerationFactory(prefsB, scopeB, installB),
                    filesB,
                )
                val stateB = currentState(repositoryB.readProtocol())
                val epochB = filesB.installEpoch()
                val dialogRepositoryB = AppDialogRepository(
                    GenerationFactory(dialogsB, scopeB, installB),
                    filesB,
                )

                assertNotEquals(epochA, epochB)
                assertEquals(epochB, stateB.installEpoch)
                assertNull(stateB.attempt)
                assertNull(stateB.activeUndo)
                assertNull(stateB.terminalOutbox)
                assertNull(dialogRepositoryB.currentDialog.first())
                assertArrayEquals(liveBytesBeforeTransfer, liveDbB.readBytes())
                assertTrue(filesA.undoFile(UndoRef(ACTIVE_OWNER))!!.exists())
                assertTrue(filesA.undoFile(UndoRef(ATTEMPT_OWNER))!!.exists())
                assertNull(filesB.undoFile(UndoRef(ACTIVE_OWNER)))
                assertNull(filesB.undoFile(UndoRef(ATTEMPT_OWNER)))
                assertNull(filesB.restoreSourceFile(RestoreSourceRef(ATTEMPT_OWNER)))
                assertEquals(
                    listOf(".publication.lock", "install_epoch"),
                    File(installB.noBackupFilesDir, "restore-recovery")
                        .listFiles()
                        .orEmpty()
                        .map(File::getName)
                        .sorted(),
                )

                val snapshotProvider = mockk<DatabaseSnapshotProvider>(relaxed = true)
                coEvery { snapshotProvider.deleteLegacyPreRestore() } returns true
                coEvery { snapshotProvider.sweepRecoveryFiles(any()) } returns
                    RestoreGarbageCollectionReport(emptyList(), emptyList())
                val replacement = mockk<DatabaseReplacement>(relaxed = true)
                val publisher = mockk<AppDialogPublisher>(relaxed = true)
                val coordinator = RestoreRecoveryCoordinator(
                    appReinitializer = mockk<AppReinitializer>(relaxed = true),
                    platformInfo = mockk<PlatformInfoProvider>(relaxed = true),
                    snapshotProvider = snapshotProvider,
                    databaseReplacement = replacement,
                    restoreStateRepository = repositoryB,
                    appDialogPublisher = publisher,
                    reporter = mockk<RestoreRecoveryReporter>(relaxed = true),
                )

                assertEquals(
                    RestoreRecoveryCoordinator.PreflightOutcome.NoOp,
                    coordinator.handlePostRestoreLaunch(),
                )
                assertFalse(coordinator.recoverySurfaceRequired)
                coVerify(exactly = 0) { replacement.rollbackFromUndo(any(), any()) }
                coVerify(exactly = 0) { snapshotProvider.inspectLiveDatabaseWithoutRoom() }
                coVerify(exactly = 0) { snapshotProvider.preserveDbBeforeMigration() }
                coVerify(exactly = 0) { publisher.publish(any()) }
                assertArrayEquals(liveBytesBeforeTransfer, liveDbB.readBytes())
            } finally {
                scopeA.coroutineContext.job.cancelAndJoin()
                scopeB.coroutineContext.job.cancelAndJoin()
            }
        }

    private suspend fun publishAttemptAssets(
        files: RestoreRecoveryFilesImpl,
        context: InstallContext,
        owner: RestoreOwnerId,
    ) {
        val undoSource = File(context.filesDir, "sources/undo_$owner.db").apply {
            parentFile!!.mkdirs()
            writeText("undo-$owner")
        }
        val restoreDownload = File(context.cacheDir, "download_$owner.db").apply {
            writeText("restore-$owner")
        }
        assertSuccess(files.publishUndo(undoSource, UndoRef(owner)))
        assertSuccess(files.publishRestoreSource(restoreDownload, RestoreSourceRef(owner)))
    }

    private fun createHealthyDatabase(file: File) {
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE install_marker(id INTEGER PRIMARY KEY, value TEXT)")
            database.execSQL("INSERT INTO install_marker(value) VALUES ('install-b')")
            database.version = APP_DATABASE_VERSION
            database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                do {
                    assertEquals("ok", cursor.getString(0))
                } while (cursor.moveToNext())
            }
        }
    }

    private fun restoreAttempt(owner: RestoreOwnerId): RestoreAttempt.Restore =
        RestoreAttempt.Restore(
            id = owner,
            phase = RestoreAttempt.Phase.Prepared,
            context = RestoreInProgressContext(
                backupSchemaVersion = APP_DATABASE_VERSION,
                backupCreatedAtEpochMs = ACTIVE_DATE - 10,
                backupAppVersion = "transfer-fixture",
                startedAtEpochMs = ACTIVE_DATE,
            ),
            undoRef = UndoRef(owner),
            sourceRef = RestoreSourceRef(owner),
        )

    private fun currentState(read: RestoreProtocolRead): RestoreProtocolState {
        assertTrue(read is RestoreProtocolRead.Current)
        return (read as RestoreProtocolRead.Current).state
    }

    private fun <T> assertSuccess(result: BackupResult<T>): T {
        assertTrue(result is BackupResult.Success)
        return (result as BackupResult.Success).data
    }

    private class InstallContext(
        base: Context,
        root: File,
    ) : ContextWrapper(base) {

        private val installFiles = File(root, "files").apply { mkdirs() }
        private val installCache = File(root, "cache").apply { mkdirs() }
        private val installNoBackup = File(root, "no_backup").apply { mkdirs() }

        override fun getFilesDir(): File = installFiles

        override fun getCacheDir(): File = installCache

        override fun getNoBackupFilesDir(): File = installNoBackup

        fun restorePreferencesFile(): File =
            File(installFiles, "datastore/restore_state_prefs.preferences_pb").also {
                it.parentFile!!.mkdirs()
            }

        fun appDialogPreferencesFile(): File =
            File(installFiles, "datastore/app_dialogs_prefs.preferences_pb").also {
                it.parentFile!!.mkdirs()
            }
    }

    /** Base constructor owns a unique throwaway store; tests use only the overridden real store. */
    private class GenerationProvider(
        file: File,
        scope: CoroutineScope,
        context: Context,
    ) : DataStoreProvider(
        name = "foreign_transfer_${UUID.randomUUID()}",
        pathResolver = AndroidDataStorePathResolver(context),
    ) {

        override val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    private class GenerationFactory(
        private val file: File,
        private val scope: CoroutineScope,
        private val context: Context,
    ) : DataStoreProviderFactory {

        override fun create(name: String): DataStoreProvider =
            GenerationProvider(file, scope, context)
    }

    private companion object {
        val ACTIVE_OWNER = owner(1)
        val ATTEMPT_OWNER = owner(2)
        const val ACTIVE_DATE = 1_700_000_000_000L

        fun owner(suffix: Int): RestoreOwnerId = RestoreOwnerId(
            "81000000-0000-4000-8000-${suffix.toString().padStart(12, '0')}",
        )
    }
}
