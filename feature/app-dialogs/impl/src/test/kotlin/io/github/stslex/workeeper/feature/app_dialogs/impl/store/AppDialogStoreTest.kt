// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.store

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
@Config(application = AppDialogStoreTest.TestApplication::class, sdk = [33])
internal class AppDialogStoreTest {

    class TestApplication : Application()

    private lateinit var context: Context
    private lateinit var store: AppDialogStore

    @BeforeEach
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.preferencesDataStoreFile(AppDialogStore.PREFS_NAME).delete()
        store = AppDialogStore(context)
    }

    @AfterEach
    fun tearDown() {
        context.preferencesDataStoreFile(AppDialogStore.PREFS_NAME).delete()
    }

    @Test
    fun `currentDialog is null when no flag is set`() = runTest {
        assertNull(store.currentDialog.first())
    }

    @Test
    fun `publish RestoreSuccess surfaces the variant with payload`() = runTest {
        val expected = AppDialog.RestoreSuccess(
            restoredAtEpochMs = 1_700_000_000_000L,
            previousVersionAvailable = true,
        )
        store.publish(expected)
        assertEquals(expected, store.currentDialog.first())
    }

    @Test
    fun `publish RestoreFailure surfaces the variant with reason`() = runTest {
        val expected = AppDialog.RestoreFailure(reason = BackupErrorCode.MissingMigrationPath)
        store.publish(expected)
        assertEquals(expected, store.currentDialog.first())
    }

    @Test
    fun `publish UndoRestoreConfirmation surfaces with date`() = runTest {
        val expected = AppDialog.UndoRestoreConfirmation(originalDataDateEpochMs = 1_650_000_000_000L)
        store.publish(expected)
        assertEquals(expected, store.currentDialog.first())
    }

    @Test
    fun `publish UndoRestoreSuccess surfaces the data object`() = runTest {
        store.publish(AppDialog.UndoRestoreSuccess)
        assertSame(AppDialog.UndoRestoreSuccess, store.currentDialog.first())
    }

    @Test
    fun `publish RestoreSuccess twice keeps the first payload`() = runTest {
        val first = AppDialog.RestoreSuccess(restoredAtEpochMs = 100L, previousVersionAvailable = true)
        val second = AppDialog.RestoreSuccess(restoredAtEpochMs = 200L, previousVersionAvailable = false)
        store.publish(first)
        store.publish(second)
        // Dedup: first wins; second is silently dropped.
        assertEquals(first, store.currentDialog.first())
    }

    @Test
    fun `RestoreFailure outranks RestoreSuccess via priority order`() = runTest {
        store.publish(AppDialog.RestoreSuccess(restoredAtEpochMs = 100L, previousVersionAvailable = false))
        store.publish(AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown))
        val current = store.currentDialog.first()
        assertEquals(BackupErrorCode.Unknown, (current as AppDialog.RestoreFailure).reason)
    }

    @Test
    fun `dismissing RestoreFailure reveals the still-pending RestoreSuccess`() = runTest {
        val success = AppDialog.RestoreSuccess(restoredAtEpochMs = 100L, previousVersionAvailable = true)
        val failure = AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown)
        store.publish(success)
        store.publish(failure)
        assertEquals(failure, store.currentDialog.first())
        store.dismiss(failure)
        assertEquals(success, store.currentDialog.first())
    }

    @Test
    fun `dismiss the only pending variant emits null`() = runTest {
        val dialog = AppDialog.RestoreSuccess(restoredAtEpochMs = 100L, previousVersionAvailable = false)
        store.publish(dialog)
        store.dismiss(dialog)
        assertNull(store.currentDialog.first())
    }

    @Test
    fun `dedup is per-variant — pending RestoreFailure does not block RestoreSuccess publish`() =
        runTest {
            val failure = AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown)
            val success = AppDialog.RestoreSuccess(restoredAtEpochMs = 1L, previousVersionAvailable = false)
            store.publish(failure)
            store.publish(success)
            // Failure still wins priority, but the success payload should be persisted
            // (verifiable after dismissing failure).
            store.dismiss(failure)
            assertEquals(success, store.currentDialog.first())
        }

    @Test
    fun `publish then dismiss then publish same variant succeeds (flag was cleared)`() = runTest {
        val first = AppDialog.RestoreSuccess(restoredAtEpochMs = 100L, previousVersionAvailable = true)
        val second = AppDialog.RestoreSuccess(restoredAtEpochMs = 200L, previousVersionAvailable = false)
        store.publish(first)
        store.dismiss(first)
        store.publish(second)
        // Cleared between the two publishes — second now wins.
        assertEquals(second, store.currentDialog.first())
    }

    // Note: a "process restart" simulation test would require cancelling the
    // DataStore's internal scope before recreating, because Preferences DataStore
    // enforces singleton-per-file at runtime. Cross-restart persistence is the
    // DataStore library's responsibility; the publish-then-read tests above
    // exercise the same persistence path through the file storage layer.

    @Test
    fun `RestoreSuccess priority is below RestoreFailure and above UndoRestoreSuccess`() = runTest {
        val success = AppDialog.RestoreSuccess(restoredAtEpochMs = 100L, previousVersionAvailable = false)
        store.publish(success)
        store.publish(AppDialog.UndoRestoreSuccess)
        assertEquals(success, store.currentDialog.first())
    }

    @Test
    fun `UndoRestoreSuccess outranks UndoRestoreConfirmation`() = runTest {
        store.publish(AppDialog.UndoRestoreConfirmation(originalDataDateEpochMs = 50L))
        store.publish(AppDialog.UndoRestoreSuccess)
        assertSame(AppDialog.UndoRestoreSuccess, store.currentDialog.first())
    }
}
