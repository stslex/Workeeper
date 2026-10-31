// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings

import android.content.Intent
import android.content.IntentSender
import io.github.stslex.workeeper.feature.settings.mvi.handler.BackupClickHandler
import io.github.stslex.workeeper.feature.settings.mvi.mapper.SettingsAuthMapper
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class SettingsPlatformAdaptersTest {

    @Test
    fun authRequestPreservesIntentSenderIdentity() {
        val sender = mockk<IntentSender>()
        assertSame(sender, SettingsAuthMapper.toIntentSender(SettingsAuthMapper.toRequest(sender)))
    }

    @Test
    fun authResultPreservesIntentIdentity() {
        val intent = mockk<Intent>()
        assertSame(intent, SettingsAuthMapper.toIntent(SettingsAuthMapper.toResult(intent)))
    }

    @Test
    fun cancelledAuthResultPreservesNull() {
        assertNull(SettingsAuthMapper.toIntent(SettingsAuthMapper.toResult(null)))
    }

    @Test
    fun androidHandlerRemainsAvailable() {
        val handler = BackupClickHandler(
            interactor = mockk(),
            preferencesRepository = mockk(),
            autoBackupController = mockk(),
            restoreStateRepository = mockk(),
            appDialogPublisher = mockk(),
            context = mockk(),
            store = mockk(),
        )
        assertTrue(handler.isAvailable)
    }
}
