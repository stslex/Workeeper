// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.mapper

import android.content.Context
import android.text.format.Formatter
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.feature.settings.domain.model.AccountDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupAuthDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupSummaryDomain
import io.github.stslex.workeeper.feature.settings.mvi.mapper.BackupUiMapper.toConfirmation
import io.github.stslex.workeeper.feature.settings.mvi.mapper.BackupUiMapper.toUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupAuthUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupErrorUi
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class BackupUiMapperTest {

    @AfterEach
    fun tearDown() {
        unmockkStatic(Formatter::class)
    }

    @Test
    fun `BackupAuthDomain NotAuthenticated maps to NotAuthenticated`() {
        assertEquals(BackupAuthUi.NotAuthenticated, BackupAuthDomain.NotAuthenticated.toUi())
    }

    @Test
    fun `BackupAuthDomain Authenticated maps email and displayName`() {
        val ui = BackupAuthDomain.Authenticated(
            AccountDomain(email = "a@b.com", displayName = "Alice"),
        ).toUi()
        assertEquals(BackupAuthUi.Authenticated(email = "a@b.com", displayName = "Alice"), ui)
    }

    @Test
    fun `BackupAuthDomain Authenticated allows null displayName`() {
        val ui = BackupAuthDomain.Authenticated(
            AccountDomain(email = "a@b.com", displayName = null),
        ).toUi()
        assertEquals(BackupAuthUi.Authenticated(email = "a@b.com", displayName = null), ui)
    }

    @Test
    fun `BackupError exhaustive mapping`() {
        val cases: Map<BackupError, BackupErrorUi> = mapOf(
            BackupError.NotAuthenticated to BackupErrorUi.NOT_AUTHENTICATED,
            BackupError.NetworkUnavailable to BackupErrorUi.NETWORK_UNAVAILABLE,
            BackupError.AuthRevoked to BackupErrorUi.AUTH_REVOKED,
            BackupError.MissingRequiredScope to BackupErrorUi.MISSING_REQUIRED_SCOPE,
            BackupError.StorageQuotaExceeded to BackupErrorUi.STORAGE_QUOTA_EXCEEDED,
            BackupError.CorruptedBackup("dummy") to BackupErrorUi.CORRUPTED_BACKUP,
            BackupError.BackupTooNew(backupSchemaVersion = 6, appSchemaVersion = 5)
                to BackupErrorUi.BACKUP_TOO_NEW,
            BackupError.MissingMigrationPath(backupSchemaVersion = 3, appSchemaVersion = 5)
                to BackupErrorUi.MISSING_MIGRATION_PATH,
            BackupError.Io(java.io.IOException("io")) to BackupErrorUi.IO_ERROR,
            BackupError.Unknown(RuntimeException("u")) to BackupErrorUi.UNKNOWN,
        )
        cases.forEach { (input, expected) ->
            assertEquals(expected, BackupUiMapper.run { input.toUi() }, "failed for $input")
        }
    }

    @Test
    fun `toConfirmation emits non-empty formatted fields`() {
        mockkStatic(Formatter::class)
        every { Formatter.formatShortFileSize(any(), any()) } returns "1.0 KB"
        val context = mockk<Context>(relaxed = true)
        val summary = BackupSummaryDomain(
            createdAtEpochMs = 1_700_000_000_000L,
            sizeBytes = 1024L,
            appVersion = "1.2.3",
            schemaVersion = 5,
        )
        val ui = summary.toConfirmation(context)
        assertEquals("1.0 KB", ui.sizeFormatted)
        assertTrue(ui.createdAtFormatted.isNotEmpty())
    }
}
