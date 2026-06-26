// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive

import android.app.Application
import android.content.Context
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.SnapshotStorage
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferences
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.database.export.DatabaseJsonExporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(application = SnapshotExportRunnerImplTest.TestApplication::class, sdk = [33])
internal class SnapshotExportRunnerImplTest {

    class TestApplication : Application()

    private val preferences = mockk<BackupPreferencesRepository>(relaxed = true)
    private val backupAuth = mockk<BackupAuth>(relaxed = true)
    private val exporter = mockk<DatabaseJsonExporter>(relaxed = true)
    private val snapshotStorage = mockk<SnapshotStorage>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    @BeforeEach
    fun setUpLogger() {
        // The runner builds its logger via Log.tag(TAG) at construction; stub it so the
        // transient-vs-non-fatal classification (w vs e) is observable and the real Crashlytics
        // facade isn't touched.
        mockkObject(Log)
        every { Log.tag(any()) } returns logger
    }

    @AfterEach
    fun tearDownLogger() {
        unmockkObject(Log)
    }

    private fun runner() = SnapshotExportRunnerImpl(
        preferences = preferences,
        backupAuth = backupAuth,
        exporter = exporter,
        snapshotStorage = snapshotStorage,
        context = context,
        dispatcher = UnconfinedTestDispatcher(),
    )

    private fun markEligible() {
        every { preferences.observe() } returns flowOf(BackupPreferences.DEFAULT.copy(aiExportEnabled = true))
        every { backupAuth.observeDriveFileGranted() } returns flowOf(true)
        coEvery { exporter.export(any(), any(), any()) } returns "{}".toByteArray()
        coEvery { snapshotStorage.uploadSnapshot(any()) } returns BackupResult.Success(Unit)
    }

    @Test
    fun `no-op when the toggle is disabled`() = runTest {
        every { preferences.observe() } returns flowOf(BackupPreferences.DEFAULT.copy(aiExportEnabled = false))

        runner().runIfEligible()

        coVerify(exactly = 0) { exporter.export(any(), any(), any()) }
        coVerify(exactly = 0) { snapshotStorage.uploadSnapshot(any()) }
    }

    @Test
    fun `no-op when drive_file is not granted`() = runTest {
        every { preferences.observe() } returns flowOf(BackupPreferences.DEFAULT.copy(aiExportEnabled = true))
        every { backupAuth.observeDriveFileGranted() } returns flowOf(false)

        runner().runIfEligible()

        coVerify(exactly = 0) { exporter.export(any(), any(), any()) }
        coVerify(exactly = 0) { snapshotStorage.uploadSnapshot(any()) }
    }

    @Test
    fun `exports and uploads when toggle on and drive_file granted`() = runTest {
        markEligible()

        runner().runIfEligible()

        coVerify(exactly = 1) { exporter.export(any(), any(), any()) }
        coVerify(exactly = 1) { snapshotStorage.uploadSnapshot(any()) }
    }

    @Test
    fun `swallows exporter exception and never throws`() = runTest {
        every { preferences.observe() } returns flowOf(BackupPreferences.DEFAULT.copy(aiExportEnabled = true))
        every { backupAuth.observeDriveFileGranted() } returns flowOf(true)
        coEvery { exporter.export(any(), any(), any()) } throws RuntimeException("boom")

        runner().runIfEligible() // must not throw

        coVerify(exactly = 0) { snapshotStorage.uploadSnapshot(any()) }
    }

    @Test
    fun `transient NetworkUnavailable upload failure is logged as warning, not a non-fatal`() =
        runTest {
            markEligible()
            coEvery { snapshotStorage.uploadSnapshot(any()) } returns
                BackupResult.Failure(BackupError.NetworkUnavailable)

            runner().runIfEligible() // must not throw

            // Transient -> logger.w (no Crashlytics non-fatal); never logger.e.
            verify(exactly = 1) { logger.w(any<() -> String>()) }
            verify(exactly = 0) { logger.e(any(), any()) }
        }

    @Test
    fun `transient AuthRevoked upload failure is logged as warning, not a non-fatal`() = runTest {
        markEligible()
        coEvery { snapshotStorage.uploadSnapshot(any()) } returns
            BackupResult.Failure(BackupError.AuthRevoked)

        runner().runIfEligible() // must not throw

        verify(exactly = 1) { logger.w(any<() -> String>()) }
        verify(exactly = 0) { logger.e(any(), any()) }
    }

    @Test
    fun `unexpected Io upload failure is recorded as a non-fatal via the error log`() = runTest {
        markEligible()
        coEvery { snapshotStorage.uploadSnapshot(any()) } returns
            BackupResult.Failure(BackupError.Io(IOException("disk")))

        runner().runIfEligible() // must not throw

        // Unexpected -> logger.e (records a Crashlytics non-fatal); never the transient warning.
        verify(exactly = 1) { logger.e(any(), any()) }
        verify(exactly = 0) { logger.w(any<() -> String>()) }
    }

    @Test
    fun `runIfEligibleAwaiting exports and uploads when toggle on and drive_file granted`() =
        runTest {
            markEligible()

            runner().runIfEligibleAwaiting()

            coVerify(exactly = 1) { exporter.export(any(), any(), any()) }
            coVerify(exactly = 1) { snapshotStorage.uploadSnapshot(any()) }
        }

    @Test
    fun `runIfEligibleAwaiting is a no-op when the toggle is disabled`() = runTest {
        every { preferences.observe() } returns flowOf(BackupPreferences.DEFAULT.copy(aiExportEnabled = false))

        runner().runIfEligibleAwaiting()

        coVerify(exactly = 0) { exporter.export(any(), any(), any()) }
        coVerify(exactly = 0) { snapshotStorage.uploadSnapshot(any()) }
    }
}
