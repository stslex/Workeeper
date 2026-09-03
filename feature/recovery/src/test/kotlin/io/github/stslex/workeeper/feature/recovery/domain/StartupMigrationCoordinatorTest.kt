// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.domain

import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.migration.APP_DATABASE_VERSION
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.core.data.database.snapshot.LiveDatabaseLocator
import io.github.stslex.workeeper.feature.recovery.diagnostics.StartupMigrationReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException

internal class StartupMigrationCoordinatorTest {

    private val snapshotProvider = mockk<DatabaseSnapshotProvider>(relaxed = true)
    private val liveDatabaseLocator = mockk<LiveDatabaseLocator>(relaxed = true)
    private val reporter = mockk<StartupMigrationReporter>(relaxed = true)
    private lateinit var coordinator: StartupMigrationCoordinator

    private lateinit var liveDbFile: File

    @BeforeEach
    fun setUp() {
        // Mock the database file via a real temp file so File.exists() can be
        // toggled by the tests.
        liveDbFile = File.createTempFile("app", ".db").apply {
            // Default: file exists with arbitrary bytes; tests delete it when
            // they want to exercise the fresh-install branch.
            writeText("placeholder")
        }
        every { liveDatabaseLocator.liveDatabaseFile() } returns liveDbFile
        coordinator = StartupMigrationCoordinator(
            snapshotProvider = snapshotProvider,
            liveDatabaseLocator = liveDatabaseLocator,
            reporter = reporter,
        )
    }

    @Test
    fun `fresh install with no live db file proceeds and clears stale snapshot`() = runTest {
        liveDbFile.delete()
        val result = coordinator.checkAndRouteOrProceed()
        assertEquals(StartupCheck.Proceed, result)
        assertNull(coordinator.lastRecoveryExportOutcome)
        coVerify(exactly = 1) { snapshotProvider.deleteRecoveryExport() }
        coVerify(exactly = 0) { snapshotProvider.preserveDbBeforeMigration() }
        coVerify(exactly = 0) {
            reporter.recordStartupMigrationFailure(any(), any(), any(), any())
        }
    }

    @Test
    fun `live db at the same schema as code proceeds and clears stale snapshot`() = runTest {
        coEvery {
            snapshotProvider.peekSnapshotSchemaVersion(liveDbFile)
        } returns BackupResult.Success(APP_DATABASE_VERSION)

        val result = coordinator.checkAndRouteOrProceed()

        assertEquals(StartupCheck.Proceed, result)
        assertNull(coordinator.lastRecoveryExportOutcome)
        coVerify(exactly = 1) { snapshotProvider.deleteRecoveryExport() }
        coVerify(exactly = 0) { snapshotProvider.preserveDbBeforeMigration() }
        coVerify(exactly = 0) {
            reporter.recordStartupMigrationFailure(any(), any(), any(), any())
        }
    }

    @Test
    fun `older live db with a registered migration path proceeds without preserving`() = runTest {
        val older = APP_DATABASE_VERSION - 1
        coEvery {
            snapshotProvider.peekSnapshotSchemaVersion(liveDbFile)
        } returns BackupResult.Success(older)
        every {
            snapshotProvider.hasMigrationPath(older, APP_DATABASE_VERSION)
        } returns true

        val result = coordinator.checkAndRouteOrProceed()

        assertEquals(StartupCheck.Proceed, result)
        assertNull(coordinator.lastRecoveryExportOutcome)
        coVerify(exactly = 1) { snapshotProvider.deleteRecoveryExport() }
        coVerify(exactly = 0) { snapshotProvider.preserveDbBeforeMigration() }
        coVerify(exactly = 0) {
            reporter.recordStartupMigrationFailure(any(), any(), any(), any())
        }
    }

    @Test
    fun `older live db without a migration path routes to recovery and preserves snapshot`() =
        runTest {
            val older = APP_DATABASE_VERSION - 1
            coEvery {
                snapshotProvider.peekSnapshotSchemaVersion(liveDbFile)
            } returns BackupResult.Success(older)
            every {
                snapshotProvider.hasMigrationPath(older, APP_DATABASE_VERSION)
            } returns false
            coEvery { snapshotProvider.preserveDbBeforeMigration() } returns
                BackupResult.Success(liveDbFile)

            val result = coordinator.checkAndRouteOrProceed()

            assertTrue(result is StartupCheck.RouteToRecovery)
            assertEquals(
                StartupMigrationFailureReason.NO_MIGRATION_PATH,
                (result as StartupCheck.RouteToRecovery).reason,
            )
            assertEquals(
                RecoveryExportOutcome.Available,
                coordinator.lastRecoveryExportOutcome,
            )
            coVerify(exactly = 1) { snapshotProvider.preserveDbBeforeMigration() }
            coVerify(exactly = 0) { snapshotProvider.deleteRecoveryExport() }
            coVerify(exactly = 1) {
                reporter.recordStartupMigrationFailure(
                    exception = null,
                    fromSchema = older,
                    toSchema = APP_DATABASE_VERSION,
                    reason = StartupMigrationFailureReason.NO_MIGRATION_PATH,
                )
            }
        }

    @Test
    fun `live db newer than code routes to recovery with APP_DOWNGRADE reason`() = runTest {
        val newer = APP_DATABASE_VERSION + 1
        coEvery {
            snapshotProvider.peekSnapshotSchemaVersion(liveDbFile)
        } returns BackupResult.Success(newer)
        coEvery { snapshotProvider.preserveDbBeforeMigration() } returns
            BackupResult.Success(liveDbFile)

        val result = coordinator.checkAndRouteOrProceed()

        assertTrue(result is StartupCheck.RouteToRecovery)
        assertEquals(
            StartupMigrationFailureReason.APP_DOWNGRADE,
            (result as StartupCheck.RouteToRecovery).reason,
        )
        assertEquals(RecoveryExportOutcome.Available, coordinator.lastRecoveryExportOutcome)
        coVerify(exactly = 1) { snapshotProvider.preserveDbBeforeMigration() }
        coVerify(exactly = 1) {
            reporter.recordStartupMigrationFailure(
                exception = null,
                fromSchema = newer,
                toSchema = APP_DATABASE_VERSION,
                reason = StartupMigrationFailureReason.APP_DOWNGRADE,
            )
        }
    }

    @Test
    fun `peek failure on the live db routes to recovery with CANNOT_PEEK_LIVE_DB reason`() =
        runTest {
            coEvery {
                snapshotProvider.peekSnapshotSchemaVersion(liveDbFile)
            } returns BackupResult.Failure(BackupError.CorruptedBackup("magic mismatch"))
            coEvery { snapshotProvider.preserveDbBeforeMigration() } returns
                BackupResult.Success(liveDbFile)

            val result = coordinator.checkAndRouteOrProceed()

            assertTrue(result is StartupCheck.RouteToRecovery)
            assertEquals(
                StartupMigrationFailureReason.CANNOT_PEEK_LIVE_DB,
                (result as StartupCheck.RouteToRecovery).reason,
            )
            assertEquals(
                RecoveryExportOutcome.Available,
                coordinator.lastRecoveryExportOutcome,
            )
            coVerify(exactly = 1) {
                reporter.recordStartupMigrationFailure(
                    exception = null,
                    fromSchema = -1,
                    toSchema = APP_DATABASE_VERSION,
                    reason = StartupMigrationFailureReason.CANNOT_PEEK_LIVE_DB,
                )
            }
        }

    @Test
    fun `route-to-recovery still completes when snapshot preserve fails`() = runTest {
        val older = APP_DATABASE_VERSION - 1
        coEvery {
            snapshotProvider.peekSnapshotSchemaVersion(liveDbFile)
        } returns BackupResult.Success(older)
        every {
            snapshotProvider.hasMigrationPath(older, APP_DATABASE_VERSION)
        } returns false
        coEvery { snapshotProvider.preserveDbBeforeMigration() } returns
            BackupResult.Failure(BackupError.Io(IOException("ENOSPC")))

        val result = coordinator.checkAndRouteOrProceed()

        assertTrue(result is StartupCheck.RouteToRecovery)
        assertEquals(RecoveryExportOutcome.Failed, coordinator.lastRecoveryExportOutcome)
        coVerify(exactly = 1) {
            reporter.recordStartupMigrationFailure(any(), any(), any(), any())
        }
    }

    @Test
    fun `a recorded first-open failure becomes the decision MainActivity routes on`() = runTest {
        coEvery { snapshotProvider.preserveDbBeforeMigration() } returns
            BackupResult.Success(liveDbFile)
        val thrown = IllegalStateException("migration 7 threw")

        val result = coordinator.recordLiveDatabaseOpenFailure(thrown)

        assertEquals(
            StartupCheck.RouteToRecovery(StartupMigrationFailureReason.LIVE_DB_OPEN_FAILED),
            result,
        )
        // The routing surface itself: `MainActivity` reads `lastDecision`, nothing else.
        assertEquals(result, coordinator.lastDecision)
        // Same treatment as a peek failure, so the recovery screen has a raw export to offer.
        assertEquals(RecoveryExportOutcome.Available, coordinator.lastRecoveryExportOutcome)
        coVerify(exactly = 1) {
            reporter.recordStartupMigrationFailure(
                exception = thrown,
                fromSchema = -1,
                toSchema = APP_DATABASE_VERSION,
                reason = StartupMigrationFailureReason.LIVE_DB_OPEN_FAILED,
            )
        }
    }

    @Test
    fun `thrown export failure is visible while startup still routes to recovery`() = runTest {
        val older = APP_DATABASE_VERSION - 1
        coEvery {
            snapshotProvider.peekSnapshotSchemaVersion(liveDbFile)
        } returns BackupResult.Success(older)
        every {
            snapshotProvider.hasMigrationPath(older, APP_DATABASE_VERSION)
        } returns false
        coEvery { snapshotProvider.preserveDbBeforeMigration() } throws IOException("ENOSPC")

        val result = coordinator.checkAndRouteOrProceed()

        assertEquals(
            StartupCheck.RouteToRecovery(StartupMigrationFailureReason.NO_MIGRATION_PATH),
            result,
        )
        assertEquals(RecoveryExportOutcome.Failed, coordinator.lastRecoveryExportOutcome)
        coVerify(exactly = 1) {
            reporter.recordStartupMigrationFailure(
                exception = null,
                fromSchema = older,
                toSchema = APP_DATABASE_VERSION,
                reason = StartupMigrationFailureReason.NO_MIGRATION_PATH,
            )
        }
    }
}
