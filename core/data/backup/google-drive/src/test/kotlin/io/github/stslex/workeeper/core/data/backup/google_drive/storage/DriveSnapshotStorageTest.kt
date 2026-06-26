// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.storage

import android.app.Application
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.AccountDataStore
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.TokenInvalidator
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveApi
import io.github.stslex.workeeper.core.data.backup.google_drive.network.DriveFileDto
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(application = DriveSnapshotStorageTest.TestApplication::class, sdk = [33])
internal class DriveSnapshotStorageTest {

    class TestApplication : Application()

    private lateinit var driveApi: DriveApi
    private lateinit var accountStore: AccountDataStore
    private lateinit var tokenInvalidator: TokenInvalidator
    private lateinit var storage: DriveSnapshotStorage

    @BeforeEach
    fun setup() {
        driveApi = mockk(relaxed = false)
        accountStore = mockk(relaxed = true)
        tokenInvalidator = mockk(relaxed = true)
        storage = DriveSnapshotStorage(
            driveApi = driveApi,
            accountStore = accountStore,
            tokenInvalidator = tokenInvalidator,
            dispatcher = UnconfinedTestDispatcher(),
        )
    }

    @Test
    fun `creates the folder when none is cached or present and caches its id`() = runTest {
        coEvery { accountStore.snapshotFolderId() } returns null
        coEvery { driveApi.listFiles(eq(DRIVE), match { it.isFolderQuery() }) } returns emptyList()
        coEvery { driveApi.createFolder(FOLDER) } returns folder("new-folder")
        coEvery { driveApi.uploadMultipart(match { it.parents == listOf("new-folder") }, any<ByteArray>()) } returns
            snapshot("up", 1L)
        coEvery { driveApi.listFiles(eq(DRIVE), match { it.isExportQuery() }) } returns listOf(snapshot("up", 1L))

        val result = storage.uploadSnapshot("{}".toByteArray())

        assertTrue(result is BackupResult.Success)
        coVerify(exactly = 1) { driveApi.createFolder(FOLDER) }
        coVerify { accountStore.setSnapshotFolderId("new-folder") }
    }

    @Test
    fun `reuses the cached folder id without listing or creating`() = runTest {
        coEvery { accountStore.snapshotFolderId() } returns "cached-folder"
        coEvery { driveApi.uploadMultipart(any(), any<ByteArray>()) } returns snapshot("up", 1L)
        coEvery { driveApi.listFiles(eq(DRIVE), match { it.isExportQuery() }) } returns listOf(snapshot("up", 1L))

        val result = storage.uploadSnapshot("{}".toByteArray())

        assertTrue(result is BackupResult.Success)
        coVerify(exactly = 0) { driveApi.createFolder(any()) }
        coVerify { driveApi.uploadMultipart(match { it.parents == listOf("cached-folder") }, any<ByteArray>()) }
    }

    @Test
    fun `reuses the oldest existing folder when several exist and none is cached`() = runTest {
        coEvery { accountStore.snapshotFolderId() } returns null
        coEvery { driveApi.listFiles(eq(DRIVE), match { it.isFolderQuery() }) } returns listOf(
            folder("newer", createdTime = "2026-02-01T00:00:00Z"),
            folder("older", createdTime = "2026-01-01T00:00:00Z"),
        )
        coEvery { driveApi.uploadMultipart(any(), any<ByteArray>()) } returns snapshot("up", 1L)
        coEvery { driveApi.listFiles(eq(DRIVE), match { it.isExportQuery() }) } returns listOf(snapshot("up", 1L))

        storage.uploadSnapshot("{}".toByteArray())

        coVerify(exactly = 0) { driveApi.createFolder(any()) }
        coVerify { accountStore.setSnapshotFolderId("older") }
        coVerify { driveApi.uploadMultipart(match { it.parents == listOf("older") }, any<ByteArray>()) }
    }

    @Test
    fun `recreates the folder and retries once when the cached id 404s`() = runTest {
        val notFound = mockk<ClientRequestException>(relaxed = true)
        every { notFound.response } returns mockk(relaxed = true) {
            every { status } returns HttpStatusCode.NotFound
        }
        coEvery { accountStore.snapshotFolderId() } returns "stale-folder"
        coEvery { driveApi.uploadMultipart(match { it.parents == listOf("stale-folder") }, any<ByteArray>()) } throws
            notFound
        coEvery { driveApi.createFolder(FOLDER) } returns folder("fresh-folder")
        coEvery { driveApi.uploadMultipart(match { it.parents == listOf("fresh-folder") }, any<ByteArray>()) } returns
            snapshot("up", 1L)
        coEvery { driveApi.listFiles(eq(DRIVE), match { it.isExportQuery() }) } returns listOf(snapshot("up", 1L))

        val result = storage.uploadSnapshot("{}".toByteArray())

        assertTrue(result is BackupResult.Success)
        coVerify { accountStore.setSnapshotFolderId(null) }
        coVerify { driveApi.createFolder(FOLDER) }
        coVerify { driveApi.uploadMultipart(match { it.parents == listOf("fresh-folder") }, any<ByteArray>()) }
    }

    @Test
    fun `rotation deletes the oldest snapshots beyond the cap after upload`() = runTest {
        coEvery { accountStore.snapshotFolderId() } returns "folder"
        coEvery { driveApi.uploadMultipart(any(), any<ByteArray>()) } returns snapshot("new", 500L)
        coEvery { driveApi.listFiles(eq(DRIVE), match { it.isExportQuery() }) } returns listOf(
            snapshot("e1", 100L),
            snapshot("e2", 200L),
            snapshot("e3", 300L),
            snapshot("e4", 400L),
        )
        coEvery { driveApi.deleteFile(any()) } just Runs

        storage.uploadSnapshot("{}".toByteArray())

        coVerify(exactly = 1) { driveApi.deleteFile("e1") }
        coVerify(exactly = 0) { driveApi.deleteFile("e2") }
        coVerify(exactly = 0) { driveApi.deleteFile("new") }
    }

    @Test
    fun `upload failure surfaces as a typed Failure (best-effort, never throws)`() = runTest {
        coEvery { accountStore.snapshotFolderId() } returns "folder"
        coEvery { driveApi.uploadMultipart(any(), any<ByteArray>()) } throws IOException("offline")

        val result = storage.uploadSnapshot("{}".toByteArray())

        assertTrue(result is BackupResult.Failure)
        assertEquals(BackupError.NetworkUnavailable, (result as BackupResult.Failure).error)
    }

    private fun folder(id: String, createdTime: String? = null): DriveFileDto = DriveFileDto(
        id = id,
        name = FOLDER,
        createdTime = createdTime,
        size = null,
        appProperties = null,
    )

    private fun snapshot(id: String, epochMs: Long): DriveFileDto = DriveFileDto(
        id = id,
        name = "workeeper_export_$epochMs.json",
        createdTime = null,
        size = null,
        appProperties = null,
    )

    private fun String.isFolderQuery(): Boolean = contains("mimeType=") && contains("name='$FOLDER'")

    private fun String.isExportQuery(): Boolean = contains("in parents")

    private companion object {
        const val DRIVE = "drive"
        const val FOLDER = "Workeeper"
    }
}
