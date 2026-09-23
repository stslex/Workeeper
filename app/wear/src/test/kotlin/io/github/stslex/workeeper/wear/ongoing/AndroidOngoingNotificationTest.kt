// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ongoing

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.wear.ongoing.OngoingActivity
import io.github.stslex.workeeper.wear.MainActivity
import io.github.stslex.workeeper.wear.R
import io.github.stslex.workeeper.wear.cache.ElapsedRealtimeClock
import io.github.stslex.workeeper.wear.ongoing.AndroidOngoingNotification.Companion.CHANNEL_ID
import io.github.stslex.workeeper.wear.ongoing.AndroidOngoingNotification.Companion.DEADLINE_EXTRA
import io.github.stslex.workeeper.wear.ongoing.AndroidOngoingNotification.Companion.NOTIFICATION_ID
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
internal class AndroidOngoingNotificationApi28Test : AndroidOngoingNotificationContract()

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class AndroidOngoingNotificationApi33Test : AndroidOngoingNotificationContract()

internal abstract class AndroidOngoingNotificationContract {
    private lateinit var application: Application
    private lateinit var manager: NotificationManager
    private lateinit var adapter: AndroidOngoingNotification
    private var now = 1_000L
    private val clock = ElapsedRealtimeClock { now }

    @BeforeEach
    fun prepareNotificationEnvironment() {
        application = ApplicationProvider.getApplicationContext()
        manager = requireNotNull(application.getSystemService(NotificationManager::class.java))
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(true)
        adapter = AndroidOngoingNotification(application, clock)
    }

    @Test
    fun constructionAndPermissionInspectionDoNotPost() {
        assertTrue(adapter.permissionGranted())
        assertNull(adapter.existingDeadlineMs())
        assertTrue(manager.activeNotifications.isEmpty())
        val channel = assertNotNull(manager.getNotificationChannel(CHANNEL_ID))
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test
    fun everyPublicationRecalculatesTheRealSystemTimeout() {
        assertEquals(OngoingPostResult.Posted(11_000L), adapter.post(request(11_000L, staleTimeout = 99_000L)))
        assertDeadline(11_000L, 10_000L)
        now = 7_000L
        assertEquals(OngoingPostResult.Posted(11_000L), adapter.updateIfPresent(request(11_000L)))
        assertDeadline(11_000L, 4_000L)
        now = 10_999L
        assertEquals(OngoingPostResult.Posted(11_000L), adapter.updateIfPresent(request(11_000L)))
        assertDeadline(11_000L, 1L)
        assertEquals(1, manager.activeNotifications.size)
    }

    @Test
    fun recoveredAdapterClampsAnUpdateToTheSurvivingShorterDeadline() {
        adapter.post(request(11_000L))
        now = 2_000L
        adapter.updateIfPresent(request(7_000L))
        val recovered = AndroidOngoingNotification(application, clock)
        assertEquals(7_000L, recovered.existingDeadlineMs())
        now = 3_000L
        assertEquals(OngoingPostResult.Posted(7_000L), recovered.updateIfPresent(request(11_000L)))
        assertDeadline(7_000L, 4_000L)
    }

    @Test
    fun updatesDoNotCreateOrResurrectAMissingNotification() {
        assertEquals(OngoingPostResult.Missing, adapter.updateIfPresent(request(11_000L)))
        assertTrue(manager.activeNotifications.isEmpty())
        adapter.post(request(11_000L))
        manager.cancel(NOTIFICATION_ID)
        assertEquals(OngoingPostResult.Missing, adapter.updateIfPresent(request(11_000L)))
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun expiredDeadlineCancelsInsteadOfPostingAnUnlimitedTimeout() {
        adapter.post(request(11_000L))
        now = 11_000L
        assertEquals(OngoingPostResult.Missing, adapter.updateIfPresent(request(20_000L)))
        assertTrue(manager.activeNotifications.isEmpty())
        assertEquals(OngoingPostResult.Missing, adapter.post(request(11_000L)))
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun missingAbsoluteDeadlineCannotBeRecoveredFromRelativeTimeout() {
        adapter.permissionGranted()
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(application, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_workeeper_wear)
                .setTimeoutAfter(90_000L)
                .build(),
        )
        assertNull(adapter.existingDeadlineMs())
        assertEquals(OngoingPostResult.Missing, adapter.updateIfPresent(request(11_000L)))
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun blockedApplicationCancelsAndReportsPermissionDenial() {
        adapter.post(request(11_000L))
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(adapter.permissionGranted())
        assertEquals(OngoingPostResult.PermissionDenied, adapter.updateIfPresent(request(11_000L)))
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun blockedChannelIsPreservedAndCannotClaimRetention() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Blocked fixture", NotificationManager.IMPORTANCE_NONE),
        )
        assertFalse(adapter.permissionGranted())
        assertEquals(OngoingPostResult.PermissionDenied, adapter.post(request(11_000L)))
        val channel = assertNotNull(manager.getNotificationChannel(CHANNEL_ID))
        assertEquals(NotificationManager.IMPORTANCE_NONE, channel.importance)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun runtimePermissionIsRequiredOnlyOnApi33AndLater() {
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertFalse(adapter.permissionGranted())
            assertEquals(OngoingPostResult.PermissionDenied, adapter.post(request(11_000L)))
            assertTrue(manager.activeNotifications.isEmpty())
        } else {
            assertTrue(adapter.permissionGranted())
            assertEquals(OngoingPostResult.Posted(11_000L), adapter.post(request(11_000L)))
        }
    }

    @Test
    fun cancellationRemovesOnlyTheCoordinatorsNotification() {
        adapter.post(request(11_000L))
        manager.notify(
            NOTIFICATION_ID + 1,
            NotificationCompat.Builder(application, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_workeeper_wear)
                .build(),
        )
        adapter.cancel()
        assertNull(adapter.existingDeadlineMs())
        assertEquals(listOf(NOTIFICATION_ID + 1), manager.activeNotifications.map { it.id })
    }

    @Test
    fun workoutNotificationHasAnImmutableReentryIntentAndNoMutationActions() {
        adapter.post(request(11_000L))
        val notification = currentNotification()
        assertEquals(NotificationCompat.CATEGORY_WORKOUT, notification.category)
        assertEquals(NotificationCompat.VISIBILITY_PUBLIC, notification.visibility)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertTrue(notification.flags and Notification.FLAG_FOREGROUND_SERVICE == 0)
        assertTrue(notification.actions.isNullOrEmpty())
        assertEquals(
            application.getString(R.string.ongoing_title),
            notification.extras.getString(Notification.EXTRA_TITLE),
        )
        assertEquals(
            application.getString(R.string.ongoing_return_to_workout),
            notification.extras.getString(Notification.EXTRA_TEXT),
        )
        val pendingIntent = shadowOf(notification.contentIntent)
        assertTrue(pendingIntent.isActivity)
        assertTrue(pendingIntent.isImmutable)
        val intent = pendingIntent.savedIntent
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(Intent.FLAG_ACTIVITY_SINGLE_TOP, intent.flags)
        assertNull(intent.extras)
        assertNotNull(OngoingActivity.recoverOngoingActivity(application, NOTIFICATION_ID))
    }

    private fun request(deadline: Long, staleTimeout: Long = 90_000L) = OngoingNotificationRequest(
        snapshot = ReducerTestFixtures.active(),
        stopAtElapsedRealtimeMs = deadline,
        timeoutAfterMs = staleTimeout,
    )

    private fun currentNotification(): Notification =
        manager.activeNotifications.single { it.id == NOTIFICATION_ID }.notification

    private fun assertDeadline(deadline: Long, remaining: Long) {
        val notification = currentNotification()
        assertEquals(deadline, notification.extras.getLong(DEADLINE_EXTRA))
        assertEquals(remaining, notification.timeoutAfter)
        assertEquals(CHANNEL_ID, notification.channelId)
    }
}
