// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ongoing

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import io.github.stslex.workeeper.wear.MainActivity
import io.github.stslex.workeeper.wear.R
import io.github.stslex.workeeper.wear.cache.ElapsedRealtimeClock

internal class AndroidOngoingNotification(
    context: Context,
    private val clock: ElapsedRealtimeClock,
) : OngoingNotification {
    private val applicationContext = context.applicationContext
    private val manager = requireNotNull(applicationContext.getSystemService(NotificationManager::class.java))
    private val compatManager = NotificationManagerCompat.from(applicationContext)

    override fun permissionGranted(): Boolean {
        ensureChannel()
        val runtimePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val channelEnabled = manager.getNotificationChannel(CHANNEL_ID)?.importance?.let {
            it > NotificationManager.IMPORTANCE_NONE
        } == true
        return runtimePermission && compatManager.areNotificationsEnabled() && channelEnabled
    }

    override fun existingDeadlineMs(): Long? {
        val notification = manager.activeNotifications.firstOrNull { it.id == NOTIFICATION_ID && it.tag == null }
            ?.notification ?: return null
        val deadline = notification.extras.getLong(DEADLINE_EXTRA, 0L)
        return if (deadline > clock.nowMs()) {
            deadline
        } else {
            cancel()
            null
        }
    }

    override fun post(request: OngoingNotificationRequest): OngoingPostResult =
        publish(request.stopAtElapsedRealtimeMs, requireExisting = false)

    override fun updateIfPresent(request: OngoingNotificationRequest): OngoingPostResult =
        publish(request.stopAtElapsedRealtimeMs, requireExisting = true)

    override fun cancel() {
        manager.cancel(NOTIFICATION_ID)
    }

    private fun publish(requestedDeadline: Long, requireExisting: Boolean): OngoingPostResult {
        if (!permissionGranted()) return denied()
        var deadline = requestedDeadline
        if (requireExisting) {
            deadline = minOf(deadline, existingDeadlineMs() ?: return OngoingPostResult.Missing)
        }
        if (deadline <= clock.nowMs()) return expired()
        val builder = notificationBuilder()
        if (requireExisting) {
            // Re-read after construction: a recovered notification may have been shortened or removed.
            deadline = minOf(deadline, existingDeadlineMs() ?: return OngoingPostResult.Missing)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return denied()
        }
        val remainingMs = deadline - clock.nowMs()
        if (remainingMs <= 0L) return expired()
        // Rebuilding the same ID replaces the relative timeout; OngoingActivity.update reuses its old value.
        val notification = builder
            .setTimeoutAfter(remainingMs)
            .addExtras(Bundle().apply { putLong(DEADLINE_EXTRA, deadline) })
            .build()
        return try {
            manager.notify(NOTIFICATION_ID, notification)
            OngoingPostResult.Posted(deadline)
        } catch (_: SecurityException) {
            denied()
        }
    }

    private fun notificationBuilder(): NotificationCompat.Builder {
        val title = applicationContext.getString(R.string.ongoing_title)
        val content = applicationContext.getString(R.string.ongoing_return_to_workout)
        val touchIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_workeeper_wear)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(touchIntent)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setOngoingActivityId(NOTIFICATION_ID)
            .setStaticIcon(R.drawable.ic_workeeper_wear)
            .setTouchIntent(touchIntent)
            .setTitle(title)
            .setContentDescription(content)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setStatus(Status.forPart(Status.TextPart(content)))
            .build()
            .apply(applicationContext)
        return builder
    }

    private fun ensureChannel() {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.ongoing_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun denied(): OngoingPostResult {
        cancel()
        return OngoingPostResult.PermissionDenied
    }

    private fun expired(): OngoingPostResult {
        cancel()
        return OngoingPostResult.Missing
    }

    internal companion object {
        const val NOTIFICATION_ID = 35_501
        const val CHANNEL_ID = "ongoing_workout"
        const val DEADLINE_EXTRA = "io.github.stslex.workeeper.ongoing.stop_at_elapsed_ms"
    }
}
