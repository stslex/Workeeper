// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.data

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(application = AppDialogRepositoryTest.TestApplication::class, sdk = [33])
internal class AppDialogRepositoryTest {

    class TestApplication : Application()

    private lateinit var context: Context
    private lateinit var repository: AppDialogRepository

    @BeforeEach
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.preferencesDataStoreFile(AppDialogRepository.PREFS_NAME).delete()
        repository = AppDialogRepository(context)
    }

    @AfterEach
    fun tearDown() {
        context.preferencesDataStoreFile(AppDialogRepository.PREFS_NAME).delete()
    }

    @Test
    fun `currentDialog is null when no flag is set`() = runTest {
        assertNull(repository.currentDialog.first())
    }

    @Test
    fun `publish RestoreSuccess surfaces the variant with payload`() = runTest {
        val expected = AppDialog.RestoreSuccess(
            restoredAtEpochMs = 1_700_000_000_000L,
            previousVersionAvailable = true,
        )
        repository.publish(expected)
        assertEquals(expected, repository.currentDialog.first())
    }

    @Test
    fun `publish RestoreFailure surfaces the variant with reason`() = runTest {
        val expected = AppDialog.RestoreFailure(reason = BackupErrorCode.MissingMigrationPath)
        repository.publish(expected)
        assertEquals(expected, repository.currentDialog.first())
    }

    @Test
    fun `publish UndoRestoreConfirmation surfaces with date`() = runTest {
        val expected = AppDialog.UndoRestoreConfirmation(originalDataDateEpochMs = 1_650_000_000_000L)
        repository.publish(expected)
        assertEquals(expected, repository.currentDialog.first())
    }

    @Test
    fun `publish UndoRestoreSuccess surfaces the data object`() = runTest {
        repository.publish(AppDialog.UndoRestoreSuccess)
        assertSame(AppDialog.UndoRestoreSuccess, repository.currentDialog.first())
    }

    @Test
    fun `publish RestoreSuccess twice keeps the first payload`() = runTest {
        val first = AppDialog.RestoreSuccess(restoredAtEpochMs = 100L, previousVersionAvailable = true)
        val second = AppDialog.RestoreSuccess(restoredAtEpochMs = 200L, previousVersionAvailable = false)
        repository.publish(first)
        repository.publish(second)
        // Dedup: first wins; second is silently dropped.
        assertEquals(first, repository.currentDialog.first())
    }

    @Test
    fun `dismiss clears only the named variant's flags`() = runTest {
        val success = AppDialog.RestoreSuccess(restoredAtEpochMs = 100L, previousVersionAvailable = true)
        val failure = AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown)
        // Both pending — priority resolution is covered by AppDialogResolverTest.
        // This test pins that dismiss(failure) does not also clear success's flags.
        repository.publish(success)
        repository.publish(failure)
        repository.dismiss(failure)
        assertEquals(success, repository.currentDialog.first())
    }

    @Test
    fun `dismiss the only pending variant emits null`() = runTest {
        val dialog = AppDialog.RestoreSuccess(restoredAtEpochMs = 100L, previousVersionAvailable = false)
        repository.publish(dialog)
        repository.dismiss(dialog)
        assertNull(repository.currentDialog.first())
    }

    @Test
    fun `dedup is per-variant — pending RestoreFailure does not block RestoreSuccess publish`() =
        runTest {
            val failure = AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown)
            val success = AppDialog.RestoreSuccess(restoredAtEpochMs = 1L, previousVersionAvailable = false)
            repository.publish(failure)
            repository.publish(success)
            // Failure still wins priority, but the success payload should be persisted
            // (verifiable after dismissing failure).
            repository.dismiss(failure)
            assertEquals(success, repository.currentDialog.first())
        }

    @Test
    fun `publish then dismiss then publish same variant succeeds (flag was cleared)`() = runTest {
        val first = AppDialog.RestoreSuccess(restoredAtEpochMs = 100L, previousVersionAvailable = true)
        val second = AppDialog.RestoreSuccess(restoredAtEpochMs = 200L, previousVersionAvailable = false)
        repository.publish(first)
        repository.dismiss(first)
        repository.publish(second)
        // Cleared between the two publishes — second now wins.
        assertEquals(second, repository.currentDialog.first())
    }

    // Note: a "process restart" simulation test would require cancelling the
    // DataStore's internal scope before recreating, because Preferences DataStore
    // enforces singleton-per-file at runtime. Cross-restart persistence is the
    // DataStore library's responsibility; the publish-then-read tests above
    // exercise the same persistence path through the file storage layer.
}
