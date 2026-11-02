// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.mapper

import android.text.format.DateUtils
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.feature.settings.domain.model.BackupSummaryDomain
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupInfoUi
import io.github.stslex.workeeper.feature.settings.resources.Res
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_info_count
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_info_count_zero
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_info_last_backup_format
import io.github.stslex.workeeper.feature.settings.resources.feature_settings_backup_info_last_backup_never
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class BackupDateMapperTest {

    private val stringResolver = mockk<StringResolver>(relaxed = true)
    private val mapper = BackupDateMapper(stringResolver)

    @BeforeEach
    fun setUp() {
        mockkStatic(DateUtils::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(DateUtils::class)
    }

    @Test
    fun `formatLastBackup returns Never branch for null epochMs`() = runTest {
        coEvery { stringResolver.getString(Res.string.feature_settings_backup_info_last_backup_never) } returns
            "Never backed up"

        val result = mapper.formatLastBackup(epochMs = null, now = 0L)

        assertEquals("Never backed up", result)
    }

    @Test
    fun `formatLastBackup formats relative time via DateUtils for non-null epochMs`() = runTest {
        every { DateUtils.getRelativeTimeSpanString(any(), any(), any(), any()) } returns
            "2 hours ago"
        coEvery {
            stringResolver.getString(Res.string.feature_settings_backup_info_last_backup_format, "2 hours ago")
        } returns "Last backup: 2 hours ago"

        val result = mapper.formatLastBackup(
            epochMs = 1_700_000_000_000L,
            now = 1_700_000_000_000L + 7_200_000L,
        )

        assertEquals("Last backup: 2 hours ago", result)
    }

    @Test
    fun `formatBackupCount zero branch uses count_zero string`() = runTest {
        coEvery { stringResolver.getString(Res.string.feature_settings_backup_info_count_zero) } returns
            "No backups yet"

        val result = mapper.formatBackupCount(count = 0)

        assertEquals("No backups yet", result)
    }

    @Test
    fun `formatBackupCount negative is treated as zero`() = runTest {
        coEvery { stringResolver.getString(Res.string.feature_settings_backup_info_count_zero) } returns
            "No backups yet"

        val result = mapper.formatBackupCount(count = -1)

        assertEquals("No backups yet", result)
    }

    @Test
    fun `formatBackupCount one uses plurals`() = runTest {
        coEvery {
            stringResolver.getPluralString(Res.plurals.feature_settings_backup_info_count, 1, 1)
        } returns "1 backup stored"

        val result = mapper.formatBackupCount(count = 1)

        assertEquals("1 backup stored", result)
    }

    @Test
    fun `formatBackupCount many uses plurals`() = runTest {
        coEvery {
            stringResolver.getPluralString(Res.plurals.feature_settings_backup_info_count, 5, 5)
        } returns "5 backups stored"

        val result = mapper.formatBackupCount(count = 5)

        assertEquals("5 backups stored", result)
    }

    @Test
    fun `toInfo with empty list returns Never plus zero text`() = runTest {
        coEvery { stringResolver.getString(Res.string.feature_settings_backup_info_last_backup_never) } returns
            "Never backed up"
        coEvery { stringResolver.getString(Res.string.feature_settings_backup_info_count_zero) } returns
            "No backups yet"

        val info = mapper.toInfo(summaries = emptyList(), now = 0L)

        // `Empty`, not `Unknown`: the mapper only ever runs on a RESULT, so "we asked and there are
        // none" is the only empty it can produce. `Unknown` is the interval before the call.
        assertEquals(BackupInfoUi.Empty(backupCountText = "No backups yet"), info)
    }

    @Test
    fun `toInfo with non-empty list maps latest plus count`() = runTest {
        every { DateUtils.getRelativeTimeSpanString(any(), any(), any(), any()) } returns
            "2 hours ago"
        coEvery {
            stringResolver.getString(Res.string.feature_settings_backup_info_last_backup_format, "2 hours ago")
        } returns "Last backup: 2 hours ago"
        coEvery {
            stringResolver.getPluralString(Res.plurals.feature_settings_backup_info_count, 2, 2)
        } returns "2 backups stored"

        val info = mapper.toInfo(
            summaries = listOf(
                BackupSummaryDomain(1_700_000_000_000L, 1024L, "1.2.3", 5),
                BackupSummaryDomain(1_600_000_000_000L, 512L, "1.2.0", 5),
            ),
            now = 1_700_000_007_200_000L,
        )

        assertEquals(
            BackupInfoUi.Present(
                lastBackupText = "Last backup: 2 hours ago",
                backupCountText = "2 backups stored",
            ),
            info,
        )
    }
}
