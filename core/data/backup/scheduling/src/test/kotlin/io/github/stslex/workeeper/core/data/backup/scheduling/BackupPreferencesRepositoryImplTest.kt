// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.scheduling

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferences
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupSchedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(application = BackupPreferencesRepositoryImplTest.TestApplication::class, sdk = [33])
internal class BackupPreferencesRepositoryImplTest {

    class TestApplication : Application()

    private lateinit var context: Context
    private lateinit var repo: BackupPreferencesRepositoryImpl

    @BeforeEach
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.preferencesDataStoreFile(PREFS_FILE_NAME).delete()
        repo = BackupPreferencesRepositoryImpl(context)
    }

    @AfterEach
    fun tearDown() {
        context.preferencesDataStoreFile(PREFS_FILE_NAME).delete()
    }

    @Test
    fun `initial read returns defaults`() = runTest {
        val prefs = repo.observe().first()
        assertEquals(BackupPreferences.DEFAULT, prefs)
        assertEquals(BackupSchedule.Daily, prefs.schedule)
        assertFalse(prefs.allowOnMobileData)
        assertEquals(0L, prefs.lastAttemptAtEpochMs)
        assertEquals(0L, prefs.lastSuccessAtEpochMs)
        assertNull(prefs.lastError)
        assertFalse(prefs.autoBackupBootstrapped)
        assertFalse(prefs.aiExportEnabled)
    }

    @Test
    fun `setAiExportEnabled true is observable on next collect`() = runTest {
        repo.setAiExportEnabled(true)
        assertTrue(repo.observe().first().aiExportEnabled)
    }

    @Test
    fun `setSchedule Daily is observable on next collect`() = runTest {
        repo.setSchedule(BackupSchedule.Daily)
        assertEquals(BackupSchedule.Daily, repo.observe().first().schedule)
    }

    @Test
    fun `setAllowOnMobileData true is observable`() = runTest {
        repo.setAllowOnMobileData(true)
        assertTrue(repo.observe().first().allowOnMobileData)
    }

    @Test
    fun `setLastError AuthRevoked then null clears the error`() = runTest {
        repo.setLastError(BackupErrorCode.AuthRevoked)
        assertEquals(BackupErrorCode.AuthRevoked, repo.observe().first().lastError)

        repo.setLastError(null)
        assertNull(repo.observe().first().lastError)
    }

    @Test
    fun `setLastAttempt and setLastSuccess persist epoch values independently`() = runTest {
        repo.setLastAttempt(1_700_000_000_000L)
        repo.setLastSuccess(1_700_000_500_000L)

        val prefs = repo.observe().first()
        assertEquals(1_700_000_000_000L, prefs.lastAttemptAtEpochMs)
        assertEquals(1_700_000_500_000L, prefs.lastSuccessAtEpochMs)
    }

    @Test
    fun `setAutoBackupBootstrapped true is observable`() = runTest {
        repo.setAutoBackupBootstrapped(true)
        assertTrue(repo.observe().first().autoBackupBootstrapped)
    }

    @Test
    fun `setSchedule emits updated value on subsequent collect`() = runTest {
        assertEquals(BackupSchedule.Daily, repo.observe().first().schedule)

        repo.setSchedule(BackupSchedule.ManualOnly)

        assertEquals(BackupSchedule.ManualOnly, repo.observe().first().schedule)
    }

    @Test
    fun `setLastError persists all enum values round-trip`() = runTest {
        BackupErrorCode.entries.forEach { code ->
            repo.setLastError(code)
            assertEquals(code, repo.observe().first().lastError)
        }
    }

    private companion object {
        const val PREFS_FILE_NAME = "backup_scheduling_prefs"
    }
}
