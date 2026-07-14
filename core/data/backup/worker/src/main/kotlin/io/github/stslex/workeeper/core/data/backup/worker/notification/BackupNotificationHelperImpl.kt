// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.data.backup.api.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.data.backup.worker.R

/**
 * Owns the lifecycle of the persistent "Auto-backup paused" notification surfaced
 * by [io.github.stslex.workeeper.core.data.backup.worker.BackupWorker] on
 * [io.github.stslex.workeeper.core.data.backup.api.error.BackupError.AuthRevoked].
 *
 * The notification is dismissible (the settings banner persists regardless) and
 * is intentionally low-importance — it should not interrupt the user, only
 * surface in the shade. Channel registration is idempotent and runs on the
 * first show call rather than at app startup so app cold-start cost stays flat
 * for users who never hit the auth-revoked path.
 *
 * App-Scope Collapse Step 6 (worker de-cycle): Metro-owned via `@ContributesBinding(AppScope)`, bound to
 * the [BackupNotificationHelper] api interface (in `core:data:backup:api`) so `core:di`'s
 * `AppGraphContract` can name it without depending on this worker module. Public for cross-module
 * aggregation (D1); `Context` plain.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class BackupNotificationHelperImpl(
    private val context: Context,
) : BackupNotificationHelper {

    override fun showAuthPaused() {
        val manager = NotificationManagerCompat.from(context)
        // POST_NOTIFICATIONS is runtime on API 33+. We never prompt for it ourselves —
        // if the user denied it, the persistent banner in Settings remains the source of
        // truth and the notification is silently skipped.
        if (!manager.areNotificationsEnabled()) return
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(context.getString(R.string.core_backup_worker_notification_paused_title))
            .setContentText(context.getString(R.string.core_backup_worker_notification_paused_body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launchAppIntent())
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Race: permission revoked between the check and the call. Skip silently.
        }
    }

    override fun cancelAuthPaused() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.core_backup_worker_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun launchAppIntent(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val CHANNEL_ID = "backup_paused"
        const val NOTIFICATION_ID = 0x42_50_4E_50
        const val REQUEST_CODE = 0x42_50_4E_50
    }
}
