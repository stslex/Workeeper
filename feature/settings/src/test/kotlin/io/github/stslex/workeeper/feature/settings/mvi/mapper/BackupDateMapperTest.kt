// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.mapper

import android.content.Context
import android.content.res.Resources
import android.text.format.DateUtils
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.domain.model.BackupSummaryDomain
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class BackupDateMapperTest {

    private val resources = mockk<Resources>(relaxed = true)
    private val context = mockk<Context>(relaxed = true).apply {
        every { resources } returns this@BackupDateMapperTest.resources
    }

    @BeforeEach
    fun setUp() {
        mockkStatic(DateUtils::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(DateUtils::class)
    }

    @Test
    fun `formatLastBackup returns Never branch for null epochMs`() {
        every { context.getString(R.string.feature_settings_backup_info_last_backup_never) } returns
            "Never backed up"

        val result = BackupDateMapper.formatLastBackup(epochMs = null, context = context, now = 0L)

        assertEquals("Never backed up", result)
    }

    @Test
    fun `formatLastBackup formats relative time via DateUtils for non-null epochMs`() {
        every { DateUtils.getRelativeTimeSpanString(any(), any(), any(), any()) } returns
            "2 hours ago"
        every {
            context.getString(R.string.feature_settings_backup_info_last_backup_format, "2 hours ago")
        } returns "Last backup: 2 hours ago"

        val result = BackupDateMapper.formatLastBackup(
            epochMs = 1_700_000_000_000L,
            context = context,
            now = 1_700_000_000_000L + 7_200_000L,
        )

        assertEquals("Last backup: 2 hours ago", result)
    }

    @Test
    fun `formatBackupCount zero branch uses count_zero string`() {
        every { context.getString(R.string.feature_settings_backup_info_count_zero) } returns
            "No backups yet"

        val result = BackupDateMapper.formatBackupCount(count = 0, context = context)

        assertEquals("No backups yet", result)
    }

    @Test
    fun `formatBackupCount negative is treated as zero`() {
        every { context.getString(R.string.feature_settings_backup_info_count_zero) } returns
            "No backups yet"

        val result = BackupDateMapper.formatBackupCount(count = -1, context = context)

        assertEquals("No backups yet", result)
    }

    @Test
    fun `formatBackupCount one uses plurals`() {
        every {
            resources.getQuantityString(R.plurals.feature_settings_backup_info_count, 1, 1)
        } returns "1 backup stored"

        val result = BackupDateMapper.formatBackupCount(count = 1, context = context)

        assertEquals("1 backup stored", result)
    }

    @Test
    fun `formatBackupCount many uses plurals`() {
        every {
            resources.getQuantityString(R.plurals.feature_settings_backup_info_count, 5, 5)
        } returns "5 backups stored"

        val result = BackupDateMapper.formatBackupCount(count = 5, context = context)

        assertEquals("5 backups stored", result)
    }

    @Test
    fun `toInfo with empty list returns Never plus zero text`() {
        every { context.getString(R.string.feature_settings_backup_info_last_backup_never) } returns
            "Never backed up"
        every { context.getString(R.string.feature_settings_backup_info_count_zero) } returns
            "No backups yet"

        val info = BackupDateMapper.toInfo(summaries = emptyList(), context = context, now = 0L)

        assertEquals("Never backed up", info.lastBackupText)
        assertEquals("No backups yet", info.backupCountText)
        assertEquals(true, info.isEmpty)
    }

    @Test
    fun `toInfo with non-empty list maps latest plus count`() {
        every { DateUtils.getRelativeTimeSpanString(any(), any(), any(), any()) } returns
            "2 hours ago"
        every {
            context.getString(R.string.feature_settings_backup_info_last_backup_format, "2 hours ago")
        } returns "Last backup: 2 hours ago"
        every {
            resources.getQuantityString(R.plurals.feature_settings_backup_info_count, 2, 2)
        } returns "2 backups stored"

        val info = BackupDateMapper.toInfo(
            summaries = listOf(
                BackupSummaryDomain(1_700_000_000_000L, 1024L, "1.2.3", 5),
                BackupSummaryDomain(1_600_000_000_000L, 512L, "1.2.0", 5),
            ),
            context = context,
            now = 1_700_000_007_200_000L,
        )

        assertEquals("Last backup: 2 hours ago", info.lastBackupText)
        assertEquals("2 backups stored", info.backupCountText)
        assertEquals(false, info.isEmpty)
    }
}
