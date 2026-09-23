// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ongoing

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit

internal enum class NotificationEnableAction { NONE, REQUEST_PERMISSION, OPEN_SETTINGS }

internal data class WearNotificationAccess(
    val enabled: Boolean,
    val action: NotificationEnableAction,
)

internal fun notificationAccess(
    runtimePermissionGranted: Boolean,
    notificationsEnabled: Boolean,
    channelEnabled: Boolean,
    requestedBefore: Boolean,
    shouldShowRationale: Boolean,
): WearNotificationAccess {
    val enabled = runtimePermissionGranted && notificationsEnabled && channelEnabled
    val action = when {
        enabled -> NotificationEnableAction.NONE
        !runtimePermissionGranted && (!requestedBefore || shouldShowRationale) ->
            NotificationEnableAction.REQUEST_PERMISSION
        else -> NotificationEnableAction.OPEN_SETTINGS
    }
    return WearNotificationAccess(enabled, action)
}

/** Reads platform access; only an explicit user action may request permission or open settings. */
internal class AndroidWearNotificationAccess(private val activity: ComponentActivity) {
    private val preferences = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val manager = requireNotNull(activity.getSystemService(NotificationManager::class.java))

    fun read(): WearNotificationAccess {
        val requiresPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val runtimeGranted = !requiresPermission || ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        val channel = manager.getNotificationChannel(AndroidOngoingNotification.CHANNEL_ID)
        return notificationAccess(
            runtimePermissionGranted = runtimeGranted,
            notificationsEnabled = NotificationManagerCompat.from(activity).areNotificationsEnabled(),
            channelEnabled = channel == null || channel.importance > NotificationManager.IMPORTANCE_NONE,
            requestedBefore = preferences.getBoolean(REQUESTED_BEFORE, false),
            shouldShowRationale = requiresPermission && activity.shouldShowRequestPermissionRationale(
                Manifest.permission.POST_NOTIFICATIONS,
            ),
        )
    }

    fun markRequested() {
        preferences.edit { putBoolean(REQUESTED_BEFORE, true) }
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)

    private companion object {
        const val PREFERENCES = "wear_notification_access"
        const val REQUESTED_BEFORE = "requested_before"
    }
}
