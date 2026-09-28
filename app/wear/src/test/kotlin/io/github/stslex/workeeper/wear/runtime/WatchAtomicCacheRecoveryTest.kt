// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.wear.cache.AtomicFileRecordStorage
import io.github.stslex.workeeper.wear.cache.BootCountProvider
import io.github.stslex.workeeper.wear.cache.CacheAbsentReason
import io.github.stslex.workeeper.wear.cache.CacheFraming
import io.github.stslex.workeeper.wear.cache.CacheReadResult
import io.github.stslex.workeeper.wear.cache.ElapsedRealtimeClock
import io.github.stslex.workeeper.wear.cache.OngoingExpiryHandler
import io.github.stslex.workeeper.wear.cache.WatchSnapshotCache
import io.github.stslex.workeeper.wear.ongoing.AndroidOngoingNotification
import io.github.stslex.workeeper.wear.ongoing.AndroidOngoingNotification.Companion.DEADLINE_EXTRA
import io.github.stslex.workeeper.wear.ongoing.OngoingNotification
import io.github.stslex.workeeper.wear.ongoing.OngoingNotificationRequest
import io.github.stslex.workeeper.wear.ongoing.OngoingPolicy
import io.github.stslex.workeeper.wear.ongoing.OngoingPostResult
import io.github.stslex.workeeper.wear.ongoing.OngoingStatus
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import io.github.stslex.workeeper.wear.ui.ControllerAction
import io.github.stslex.workeeper.wear.ui.WearSurfaceKind
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File
import java.io.IOException
import java.util.Locale

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
internal class WatchAtomicCacheRecoveryApi28Test : WatchAtomicCacheRecoveryContract()

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class WatchAtomicCacheRecoveryApi33Test : WatchAtomicCacheRecoveryContract()

internal abstract class WatchAtomicCacheRecoveryContract {
    @TempDir
    lateinit var directory: File

    @Test
    fun backupOnlyRestartRestoresCanonicalDisplayWithoutRenewingNotification() {
        val env = AtomicRuntimeEnvironment(File(directory, "snapshot"))
        val owner = env.newOwner()
        val request = owner.issueHandshake()
        assertTrue(
            owner.receiveSnapshot(
                ActiveWorkoutSnapshotResponse(
                    WearProtocol.SCHEMA_VERSION,
                    request.correlationId,
                    ReducerTestFixtures.active(),
                ),
            ),
        )
        owner.onAction(ControllerAction.SetReps(12))
        owner.onAction(ControllerAction.SetWeight(null))
        assertTrue(owner.surface.value.hasUnsubmittedDraft)
        val canonicalBytes = env.cacheFile.readBytes()
        assertNotNull(CacheFraming.decode(canonicalBytes), "The backup must contain a valid framed cache")
        val backup = File("${env.cacheFile.path}.bak")
        assertTrue(env.cacheFile.renameTo(backup))
        assertFalse(env.cacheFile.exists())
        assertTrue(backup.exists())
        env.now = 2_000L

        val restarted = assertDoesNotThrow<WatchRuntimeOwner> { env.newOwner() }
        assertEquals(8, restarted.surface.value.reps, "A recoverable backup must not become an empty cache")
        assertEquals(10_000, restarted.surface.value.weightHundredthsKg)
        assertEquals(WearSurfaceKind.REFRESH_REQUIRED, restarted.surface.value.kind)
        assertFalse(restarted.surface.value.controlsEnabled)
        assertFalse(restarted.surface.value.completeEnabled)
        assertFalse(restarted.surface.value.hasUnsubmittedDraft)
        assertEquals(OngoingStatus.Scheduled(126_000L), restarted.ongoingStatus.value)
        assertEquals(1, env.posts)
        assertEquals(0, env.updates, "Restoring a runtime must not republish its notification")
        assertEquals(126_000L, env.notificationDeadline())
        assertArrayEquals(canonicalBytes, env.cacheFile.readBytes())
        assertFalse(backup.exists())

        restarted.onWake()
        assertEquals(1, env.posts)
        assertEquals(1, env.updates)
        assertEquals(126_000L, env.notificationDeadline())
        assertEquals(124_000L, env.manager.activeNotifications.single().notification.timeoutAfter)
        assertFalse(restarted.surface.value.controlsEnabled)
    }

    @Test
    fun trueAbsenceIsEmptyButUnreadableRecordRemainsAnIoFailure() {
        val file = File(directory, "snapshot")
        val storage = AtomicFileRecordStorage(file)
        assertNull(assertDoesNotThrow<ByteArray?> { storage.read() })
        assertTrue(file.mkdir())
        assertThrows(IOException::class.java) { storage.read() }
        val cache = WatchSnapshotCache(
            storage,
            ElapsedRealtimeClock { 1_000L },
            BootCountProvider { 1 },
            OngoingExpiryHandler {},
        )
        assertEquals(CacheReadResult.Absent(CacheAbsentReason.IO_FAILURE), cache.read())
    }
}

private class AtomicRuntimeEnvironment(val cacheFile: File) {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    val manager: NotificationManager = requireNotNull(application.getSystemService(NotificationManager::class.java))
    var now = 1_000L
    var posts = 0
    var updates = 0
    private val clock = ElapsedRealtimeClock { now }
    private var nextId = 0

    init {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(true)
    }

    fun newOwner(): WatchRuntimeOwner {
        val delegate = AndroidOngoingNotification(application, clock)
        val notification = object : OngoingNotification by delegate {
            override fun post(request: OngoingNotificationRequest): OngoingPostResult {
                posts += 1
                return delegate.post(request)
            }

            override fun updateIfPresent(request: OngoingNotificationRequest): OngoingPostResult {
                updates += 1
                return delegate.updateIfPresent(request)
            }
        }
        return WatchRuntimeOwner(
            storage = AtomicFileRecordStorage(cacheFile),
            clock = clock,
            bootCount = BootCountProvider { 1 },
            notification = notification,
            policy = OngoingPolicy(reconnectWindowMs = 5_000L),
            identity = RuntimeIdentity("atomic-cache-test-watch", RuntimeIdSource { ReducerTestFixtures.id(++nextId) }),
            scheduler = RecordingDeadlineScheduler(),
            selectedLocale = Locale.US,
        )
    }

    fun notificationDeadline(): Long = manager.activeNotifications.single().notification.extras.getLong(DEADLINE_EXTRA)
}
