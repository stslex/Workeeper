// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.storage

import android.app.Application
import io.github.stslex.workeeper.core.data.backup.api.BackupConstants
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.model.BackupRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.TokenInvalidator
import io.github.stslex.workeeper.core.data.backup.google_drive.error.DriveException
import io.github.stslex.workeeper.core.data.backup.google_drive.manifest.ManifestPropertiesMapper
import io.github.stslex.workeeper.core.data.backup.google_drive.manifest.ManifestPropertiesMapper.KEY_APP_VERSION
import io.github.stslex.workeeper.core.data.backup.google_drive.manifest.ManifestPropertiesMapper.KEY_CREATED_AT_EPOCH_MS
import io.github.stslex.workeeper.core.data.backup.google_drive.manifest.ManifestPropertiesMapper.KEY_DB_FILE_SIZE_BYTES
import io.github.stslex.workeeper.core.data.backup.google_drive.manifest.ManifestPropertiesMapper.KEY_DB_SCHEMA_VERSION
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveApi
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveFileDto
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveFileMetadataDto
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(application = DriveBackupStorageTest.TestApplication::class, sdk = [33])
internal class DriveBackupStorageTest {

    class TestApplication : Application()

    private lateinit var driveApi: DriveApi
    private lateinit var tokenInvalidator: TokenInvalidator
    private lateinit var storage: DriveBackupStorage

    @BeforeEach
    fun setup() {
        driveApi = mockk(relaxed = false)
        tokenInvalidator = mockk(relaxed = true)
        storage = DriveBackupStorage(
            driveApi = driveApi,
            tokenInvalidator = tokenInvalidator,
            dispatcher = UnconfinedTestDispatcher(),
        )
    }

    @Test
    fun `listBackups returns refs sorted newest-first by createdAtEpochMs`() = runTest {
        val files = listOf(
            driveFileWithManifest("a", manifestAt(100L)),
            driveFileWithManifest("b", manifestAt(300L)),
            driveFileWithManifest("c", manifestAt(200L)),
        )
        coEvery { driveApi.listFiles(any(), any()) } returns files

        val result = storage.listBackups()

        assertTrue(result is BackupResult.Success)
        val refs = (result as BackupResult.Success).data
        assertEquals(listOf("b", "c", "a"), refs.map { it.remoteId })
    }

    @Test
    fun `listBackups maps appProperties manifest into BackupRef manifest`() = runTest {
        val manifest = manifestAt(42L).copy(deviceModel = "Pixel 9")
        coEvery { driveApi.listFiles(any(), any()) } returns listOf(driveFileWithManifest("x", manifest))

        val result = storage.listBackups()

        assertTrue(result is BackupResult.Success)
        val refs = (result as BackupResult.Success).data
        assertEquals(1, refs.size)
        assertEquals(manifest, refs.single().manifest)
    }

    @Test
    fun `binary list still queries appDataFolder space with the app_ name prefix`() = runTest {
        // Regression: making DriveApi space-aware must not change the binary backup path.
        coEvery { driveApi.listFiles(any(), any()) } returns emptyList()

        storage.listBackups()

        coVerify {
            driveApi.listFiles(
                spaces = "appDataFolder",
                query = match { it.contains("'${BackupConstants.FILE_PREFIX}'") && it.contains("trashed=false") },
            )
        }
    }

    @Test
    fun `uploadBackup returns ref with id from DriveApi upload and persists manifest`() = runTest {
        val manifest = manifestAt(1_000L)
        val tempDir = File(System.getProperty("java.io.tmpdir"), "drive-storage-test")
        tempDir.mkdirs()
        val dbFile = File(tempDir, "src.db").apply { writeText("payload") }

        coEvery { driveApi.uploadMultipart(any(), eq(dbFile)) } returns
            driveFileDto(id = "uploaded-id")
        coEvery { driveApi.listFiles(any(), any()) } returns emptyList()

        val result = storage.uploadBackup(dbFile, manifest)

        assertTrue(result is BackupResult.Success)
        val ref = (result as BackupResult.Success).data
        assertEquals("uploaded-id", ref.remoteId)
        assertEquals(manifest, ref.manifest)
        coVerify {
            driveApi.uploadMultipart(
                match { meta: DriveFileMetadataDto ->
                    meta.name == "${BackupConstants.FILE_PREFIX}1000${BackupConstants.DB_FILE_SUFFIX}" &&
                        meta.parents == listOf("appDataFolder") &&
                        meta.appProperties[KEY_APP_VERSION] == manifest.appVersion &&
                        meta.appProperties[KEY_DB_SCHEMA_VERSION] ==
                        manifest.dbSchemaVersion.toString() &&
                        meta.appProperties[KEY_CREATED_AT_EPOCH_MS] ==
                        manifest.createdAtEpochMs.toString() &&
                        meta.appProperties[KEY_DB_FILE_SIZE_BYTES] ==
                        manifest.dbFileSizeBytes.toString()
                },
                eq(dbFile),
            )
        }
    }

    @Test
    fun `uploadBackup with 3 existing + new rotates oldest`() = runTest {
        val newManifest = manifestAt(400L)
        val dbFile = tempFile()
        val existingFiles = listOf(
            driveFileWithManifest("old1", manifestAt(100L)),
            driveFileWithManifest("old2", manifestAt(200L)),
            driveFileWithManifest("old3", manifestAt(300L)),
        )
        val withNew = existingFiles + driveFileWithManifest("new", newManifest)
        coEvery { driveApi.uploadMultipart(any(), any<File>()) } returns driveFileDto(id = "new")
        coEvery { driveApi.listFiles(any(), any()) } returns withNew
        coEvery { driveApi.deleteFile(any()) } just Runs

        val result = storage.uploadBackup(dbFile, newManifest)

        assertTrue(result is BackupResult.Success)
        coVerify(exactly = 1) { driveApi.deleteFile("old1") }
        coVerify(exactly = 0) { driveApi.deleteFile("old2") }
        coVerify(exactly = 0) { driveApi.deleteFile("old3") }
        coVerify(exactly = 0) { driveApi.deleteFile("new") }
    }

    @Test
    fun `uploadBackup with 2 existing + new does not rotate`() = runTest {
        val newManifest = manifestAt(300L)
        val dbFile = tempFile()
        val withNew = listOf(
            driveFileWithManifest("old1", manifestAt(100L)),
            driveFileWithManifest("old2", manifestAt(200L)),
            driveFileWithManifest("new", newManifest),
        )
        coEvery { driveApi.uploadMultipart(any(), any<File>()) } returns driveFileDto(id = "new")
        coEvery { driveApi.listFiles(any(), any()) } returns withNew

        val result = storage.uploadBackup(dbFile, newManifest)

        assertTrue(result is BackupResult.Success)
        coVerify(exactly = 0) { driveApi.deleteFile(any()) }
    }

    @Test
    fun `uploadBackup rotation list failure does not fail upload`() = runTest {
        val newManifest = manifestAt(400L)
        val dbFile = tempFile()
        coEvery { driveApi.uploadMultipart(any(), any<File>()) } returns driveFileDto(id = "new")
        coEvery { driveApi.listFiles(any(), any()) } throws IOException("rotation list failed")

        val result = storage.uploadBackup(dbFile, newManifest)

        assertTrue(result is BackupResult.Success, "upload must still succeed; got $result")
        assertEquals("new", (result as BackupResult.Success).data.remoteId)
    }

    @Test
    fun `downloadBackup writes to target and returns manifest on size match`() = runTest {
        val dbFile = tempFile()
        val manifest = manifestAt(100L).copy(dbFileSizeBytes = 1_000L)
        val ref = BackupRef("rid", manifest)
        coEvery { driveApi.downloadFile("rid", dbFile) } returns 1_000L

        val result = storage.downloadBackup(ref, dbFile)

        assertTrue(result is BackupResult.Success)
        assertEquals(manifest, (result as BackupResult.Success).data)
    }

    @Test
    fun `downloadBackup with size mismatch returns CorruptedBackup`() = runTest {
        val dbFile = tempFile()
        val manifest = manifestAt(100L).copy(dbFileSizeBytes = 1_000L)
        val ref = BackupRef("rid", manifest)
        coEvery { driveApi.downloadFile("rid", dbFile) } returns 9_999L

        val result = storage.downloadBackup(ref, dbFile)

        assertTrue(result is BackupResult.Failure)
        assertTrue((result as BackupResult.Failure).error is BackupError.CorruptedBackup)
    }

    @Test
    fun `deleteBackup calls API and returns Success`() = runTest {
        val ref = BackupRef("to-delete", manifestAt(0L))
        coEvery { driveApi.deleteFile("to-delete") } just Runs

        val result = storage.deleteBackup(ref)

        assertEquals(BackupResult.Success(Unit), result)
        coVerify { driveApi.deleteFile("to-delete") }
    }

    @Test
    fun `network IOException during list maps to NetworkUnavailable`() = runTest {
        coEvery { driveApi.listFiles(any(), any()) } throws IOException("offline")

        val result = storage.listBackups()

        assertTrue(result is BackupResult.Failure)
        assertEquals(BackupError.NetworkUnavailable, (result as BackupResult.Failure).error)
    }

    @Test
    fun `uploadBackup retries once after 401 and succeeds on second call`() = runTest {
        val manifest = manifestAt(1_000L)
        val dbFile = tempFile()
        coEvery { driveApi.uploadMultipart(any(), any<File>()) } throws
            DriveException.AuthRevoked("first 401") andThen driveFileDto(id = "fresh-id")
        coEvery { driveApi.listFiles(any(), any()) } returns emptyList()

        val result = storage.uploadBackup(dbFile, manifest)

        assertTrue(result is BackupResult.Success, "upload must succeed on retry; got $result")
        assertEquals("fresh-id", (result as BackupResult.Success).data.remoteId)
        coVerify(exactly = 1) { tokenInvalidator.invalidate() }
        coVerify(exactly = 2) { driveApi.uploadMultipart(any(), any<File>()) }
    }

    @Test
    fun `uploadBackup second 401 propagates as AuthRevoked`() = runTest {
        val manifest = manifestAt(1_000L)
        val dbFile = tempFile()
        coEvery { driveApi.uploadMultipart(any(), any<File>()) } throws
            DriveException.AuthRevoked("first 401") andThenThrows
            DriveException.AuthRevoked("second 401")

        val result = storage.uploadBackup(dbFile, manifest)

        assertTrue(result is BackupResult.Failure)
        assertEquals(BackupError.AuthRevoked, (result as BackupResult.Failure).error)
        coVerify(exactly = 1) { tokenInvalidator.invalidate() }
        coVerify(exactly = 2) { driveApi.uploadMultipart(any(), any<File>()) }
    }

    @Test
    fun `listBackups retries once after 401 and succeeds on second call`() = runTest {
        coEvery { driveApi.listFiles(any(), any()) } throws DriveException.AuthRevoked("401") andThen
            listOf(driveFileWithManifest("a", manifestAt(100L)))

        val result = storage.listBackups()

        assertTrue(result is BackupResult.Success)
        assertEquals(1, (result as BackupResult.Success).data.size)
        coVerify(exactly = 1) { tokenInvalidator.invalidate() }
        coVerify(exactly = 2) { driveApi.listFiles(any(), any()) }
    }

    @Test
    fun `deleteBackup retries once after 401 and succeeds`() = runTest {
        coEvery { driveApi.deleteFile("rid") } throws DriveException.AuthRevoked("401") andThen
            Unit

        val result = storage.deleteBackup(BackupRef("rid", manifestAt(0L)))

        assertEquals(BackupResult.Success(Unit), result)
        coVerify(exactly = 1) { tokenInvalidator.invalidate() }
        coVerify(exactly = 2) { driveApi.deleteFile("rid") }
    }

    @Test
    fun `downloadBackup retries once after 401 and succeeds`() = runTest {
        val ref = BackupRef("rid", manifestAt(0L).copy(dbFileSizeBytes = 7L))
        val dbFile = tempFile()
        coEvery { driveApi.downloadFile("rid", dbFile) } throws
            DriveException.AuthRevoked("401") andThen 7L

        val result = storage.downloadBackup(ref, dbFile)

        assertTrue(result is BackupResult.Success)
        coVerify(exactly = 1) { tokenInvalidator.invalidate() }
        coVerify(exactly = 2) { driveApi.downloadFile("rid", dbFile) }
    }

    private fun manifestAt(t: Long): BackupManifest = BackupManifest(
        appVersion = "1.43.0",
        dbSchemaVersion = 6,
        createdAtEpochMs = t,
        dbFileSizeBytes = 1_000L,
        deviceModel = null,
    )

    private fun driveFileDto(
        id: String,
        manifest: BackupManifest? = null,
    ): DriveFileDto = DriveFileDto(
        id = id,
        name = "${BackupConstants.FILE_PREFIX}$id${BackupConstants.DB_FILE_SUFFIX}",
        createdTime = null,
        size = null,
        appProperties = manifest?.let(ManifestPropertiesMapper::toAppProperties),
    )

    private fun driveFileWithManifest(id: String, manifest: BackupManifest): DriveFileDto =
        driveFileDto(id, manifest)

    private fun tempFile(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "drive-storage-test")
        dir.mkdirs()
        return File(dir, "tmp-${System.nanoTime()}.db").apply { writeText("payload") }
    }
}
