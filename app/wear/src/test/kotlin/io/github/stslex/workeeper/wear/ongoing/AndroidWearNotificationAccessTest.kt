// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ongoing

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
internal class AndroidWearNotificationAccessApi28Test : AndroidWearNotificationAccessContract()

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class AndroidWearNotificationAccessApi33Test : AndroidWearNotificationAccessContract()

internal abstract class AndroidWearNotificationAccessContract {

    @Test
    fun accessInspectionUsesPlatformVersionAndChannelStateWithoutPosting() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = controller.get()
            val manager = requireNotNull(activity.getSystemService(NotificationManager::class.java))
            val access = AndroidWearNotificationAccess(activity)
            shadowOf(activity.application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
            shadowOf(manager).setNotificationsEnabled(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                assertEquals(NotificationEnableAction.REQUEST_PERMISSION, access.read().action)
                access.markRequested()
                assertEquals(NotificationEnableAction.OPEN_SETTINGS, access.read().action)
            } else {
                assertTrue(access.read().enabled)
                assertEquals(NotificationEnableAction.NONE, access.read().action)
            }
            shadowOf(activity.application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            manager.createNotificationChannel(
                NotificationChannel(
                    AndroidOngoingNotification.CHANNEL_ID,
                    "Disabled test channel",
                    NotificationManager.IMPORTANCE_NONE,
                ),
            )
            assertFalse(access.read().enabled)
            assertEquals(NotificationEnableAction.OPEN_SETTINGS, access.read().action)
            val intent = access.settingsIntent()
            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
            assertEquals(activity.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
            assertTrue(manager.activeNotifications.isEmpty(), "Access inspection must not post a notification")
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
