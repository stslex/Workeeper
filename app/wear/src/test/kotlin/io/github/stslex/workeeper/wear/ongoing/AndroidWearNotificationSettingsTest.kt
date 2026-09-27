// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ongoing

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [30])
internal class AndroidWearNotificationSettingsApi30Test : AndroidWearNotificationSettingsContract()

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class AndroidWearNotificationSettingsApi33Test : AndroidWearNotificationSettingsContract()

internal abstract class AndroidWearNotificationSettingsContract {

    @Test
    fun supportedPerAppSettingsOpensOnlyTheRequestedPackage() = withActivity { activity ->
        assertTrue(AndroidWearNotificationAccess(activity).openSettings().isSuccess)

        val intent = activity.attempts.single()
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(activity.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun missingPerAppSettingsFallsBackToPublicSystemSettings() {
        withActivity { activity ->
            activity.failures[Settings.ACTION_APP_NOTIFICATION_SETTINGS] = ActivityNotFoundException()

            val result = assertDoesNotThrow<Result<Unit>> { AndroidWearNotificationAccess(activity).openSettings() }
            assertTrue(result.isSuccess)

            assertEquals(
                listOf(Settings.ACTION_APP_NOTIFICATION_SETTINGS, Settings.ACTION_SETTINGS),
                activity.attempts.map { it.action },
            )
            assertEquals(activity.packageName, activity.attempts.first().getStringExtra(Settings.EXTRA_APP_PACKAGE))
            assertNull(activity.attempts.last().extras)
        }
    }

    @Test
    fun noSettingsHandlerReturnsFailureAndKeepsNotificationAccessDisabled() = withActivity { activity ->
        val manager = requireNotNull(activity.getSystemService(NotificationManager::class.java))
        shadowOf(manager).setNotificationsEnabled(false)
        activity.failures[Settings.ACTION_APP_NOTIFICATION_SETTINGS] = ActivityNotFoundException()
        val failure = ActivityNotFoundException("No settings activity")
        activity.failures[Settings.ACTION_SETTINGS] = failure
        val access = AndroidWearNotificationAccess(activity)
        val before = access.read()

        val result = assertDoesNotThrow<Result<Unit>> { access.openSettings() }

        assertSame(failure, result.exceptionOrNull())
        assertEquals(2, activity.attempts.size)
        assertEquals(before, access.read())
        assertFalse(access.read().enabled)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    private fun withActivity(block: (NotificationSettingsTestActivity) -> Unit) {
        val controller = Robolectric.buildActivity(NotificationSettingsTestActivity::class.java).setup()
        try {
            block(controller.get())
        } finally {
            controller.pause().stop().destroy()
        }
    }
}

internal class NotificationSettingsTestActivity : ComponentActivity() {
    val attempts = mutableListOf<Intent>()
    val failures = mutableMapOf<String, Throwable>()

    override fun startActivity(intent: Intent) {
        attempts += intent
        failures[intent.action]?.let { throw it }
    }
}
