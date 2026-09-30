// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.BoundedDisplayName
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WatchDecodeResult
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.core.wear.protocol.WearProtocolCodec
import io.github.stslex.workeeper.wear.cache.CacheFraming
import io.github.stslex.workeeper.wear.ongoing.OngoingNotification
import io.github.stslex.workeeper.wear.ongoing.OngoingNotificationRequest
import io.github.stslex.workeeper.wear.ongoing.OngoingPostResult
import io.github.stslex.workeeper.wear.ongoing.OngoingStatus
import io.github.stslex.workeeper.wear.ongoing.WriteFailure
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import io.github.stslex.workeeper.wear.ui.CompletionUnavailableReason
import io.github.stslex.workeeper.wear.ui.WearSurfaceKind
import io.github.stslex.workeeper.wear.ui.WearSurfaceMapper
import io.github.stslex.workeeper.wear.ui.ongoingStatus
import io.github.stslex.workeeper.wear.ui.surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.Locale

internal class WatchRuntimeOwnerTest {
    @Test
    fun admittedSnapshotIsDurableAndNotifiedBeforeSurfacePublication() {
        val env = RuntimeTestEnvironment()
        env.trace.clear()
        val collection = env.recordPublications()
        try {
            env.storage.afterReplace = { assertEquals(WearSurfaceKind.LOADING, env.owner.surface.value.kind) }
            assertTrue(env.accept())
            assertEquals(listOf("cache:126000", "post:125000", "surface:ACTIVE"), env.trace)
            assertEquals(OngoingStatus.Scheduled(126_000), env.owner.ongoingStatus.value)
        } finally {
            collection.cancel()
        }
    }

    @Test
    fun diskWriteConsumingMutationWindowCannotPublishFreshControls() {
        val env = RuntimeTestEnvironment()
        val collection = env.recordPublications()
        try {
            env.storage.afterReplace = { env.now += WearProtocol.MAX_MUTATION_WINDOW_MS }
            assertTrue(env.accept())
            assertEquals(WearSurfaceKind.REFRESH_REQUIRED, env.owner.surface.value.kind)
            assertFalse(env.owner.surface.value.completeEnabled)
            assertFalse(env.trace.contains("surface:ACTIVE"))
            assertEquals(5_000L, env.notification.posts.single().timeoutAfterMs)
        } finally {
            collection.cancel()
        }
    }

    @Test
    fun draftsAreMemoryOnlyAndExplicitNullWeightIsPreserved() {
        val env = RuntimeTestEnvironment()
        env.accept()
        val originalBytes = requireNotNull(env.storage.bytes).copyOf()
        val originalReads = env.reads
        env.owner.onAction(ControllerAction.SetWeight(null))
        env.owner.onAction(ControllerAction.SetReps(12))
        assertEquals(originalReads, env.reads)
        assertTrue(originalBytes.contentEquals(env.storage.bytes))
        assertNull(env.owner.surface.value.weightHundredthsKg)
        assertEquals(12, env.owner.surface.value.reps)
        assertTrue(env.owner.surface.value.hasUnsubmittedDraft)
        assertEquals(10_000, cachedActive(env).target.weightHundredthsKg)
    }

    @Test
    fun completionIssuesFingerprintAndInFlightWithoutInventingAcknowledgement() {
        val env = RuntimeTestEnvironment()
        env.accept()
        assertFalse(env.owner.surface.value.hasUnsubmittedDraft)
        env.owner.onAction(ControllerAction.SetWeight(null))
        assertTrue(env.owner.surface.value.hasUnsubmittedDraft)
        val result = assertInstanceOf(
            WatchActionResult.CommandIssued::class.java,
            env.owner.onAction(ControllerAction.CompleteSet),
            "Completion must return an issued command bound to its fingerprint",
        )
        assertNull(result.fingerprint.weightHundredthsKg)
        assertEquals(0, result.fingerprint.setPosition)
        assertEquals(1, env.owner.surface.value.setOrdinal)
        assertEquals(CompletionUnavailableReason.COMMAND_IN_FLIGHT, env.owner.surface.value.completionUnavailableReason)
        assertFalse(env.owner.surface.value.completeEnabled)
        assertFalse(env.owner.surface.value.hasUnsubmittedDraft)
        assertNull(env.owner.surface.value.weightHundredthsKg)
        assertEquals(8, env.owner.surface.value.reps)
        assertEquals(WatchActionResult.Rejected, env.owner.onAction(ControllerAction.CompleteSet))
        assertEquals(0, cachedActive(env).target.setPosition)
    }

    @Test
    fun exactLeaseBoundaryRejectsActionAndKeepsUnsubmittedValues() {
        val env = RuntimeTestEnvironment()
        env.accept()
        env.owner.onAction(ControllerAction.SetReps(12))
        env.now = 121_000
        assertEquals(WatchActionResult.Rejected, env.owner.onAction(ControllerAction.SetReps(13)))
        assertEquals(WatchActionResult.Rejected, env.owner.onAction(ControllerAction.CompleteSet))
        assertEquals(12, env.owner.surface.value.reps)
        assertTrue(env.owner.surface.value.hasUnsubmittedDraft)
        assertEquals(CompletionUnavailableReason.REFRESH_REQUIRED, env.owner.surface.value.completionUnavailableReason)
        assertEquals(126_000L, env.scheduler.deadline)
    }

    @Test
    fun deadlineCallbackExpiresAuthorityAndThenStopsOngoingWithoutPolling() {
        val env = RuntimeTestEnvironment()
        env.accept()
        assertEquals(121_000L, env.scheduler.deadline)
        env.now = 121_000
        env.scheduler.fire()
        assertFalse(env.owner.surface.value.controlsEnabled)
        assertEquals(126_000L, env.scheduler.deadline)
        env.now = 126_000
        env.scheduler.fire()
        assertEquals(OngoingStatus.Inactive, env.owner.ongoingStatus.value)
        assertNull(env.notification.existingDeadlineMs())
        assertEquals(1_000L + WearProtocol.DISPLAY_CACHE_TTL_MS, env.scheduler.deadline)
    }

    @Test
    fun disconnectClampsOngoingBeforePublishingReadOnlyDraft() {
        val env = RuntimeTestEnvironment()
        env.accept()
        env.owner.onAction(ControllerAction.SetWeight(null))
        env.trace.clear()
        val collection = env.recordPublications()
        try {
            env.now = 2_000
            env.owner.disconnected()
            assertEquals(listOf("update:5000", "cache:7000", "surface:DISCONNECTED"), env.trace)
            assertTrue(env.owner.surface.value.hasUnsubmittedDraft)
            assertNull(env.owner.surface.value.weightHundredthsKg)
            assertFalse(env.owner.surface.value.controlsEnabled)
        } finally {
            collection.cancel()
        }
    }

    @Test
    fun compatibleHandshakePreservesDraftButSourceChangeClearsIt() {
        val env = RuntimeTestEnvironment()
        env.accept()
        env.owner.onAction(ControllerAction.SetReps(12))
        env.accept(ReducerTestFixtures.active(leaseGeneration = 2))
        assertEquals(12, env.owner.surface.value.reps)
        assertTrue(env.owner.surface.value.hasUnsubmittedDraft)
        env.accept(ReducerTestFixtures.active(revision = 2, leaseGeneration = 3))
        assertEquals(8, env.owner.surface.value.reps)
        assertFalse(env.owner.surface.value.hasUnsubmittedDraft)
    }

    @Test
    fun canonicalValuesMatchingDraftClearItsUnsubmittedSignal() {
        val env = RuntimeTestEnvironment()
        env.accept()
        env.owner.onAction(ControllerAction.SetReps(12))
        val incoming = ReducerTestFixtures.active(leaseGeneration = 2)
        val payload = incoming.payload as SnapshotPayload.ActiveWithTarget
        env.accept(incoming.copy(payload = payload.copy(target = payload.target.copy(reps = 12))))
        assertEquals(12, env.owner.surface.value.reps)
        assertFalse(env.owner.surface.value.hasUnsubmittedDraft)
    }

    @Test
    fun terminalSnapshotCancelsAndPersistsBeforePublication() {
        val env = RuntimeTestEnvironment()
        env.accept()
        env.owner.onAction(ControllerAction.SetReps(12))
        env.trace.clear()
        val collection = env.recordPublications()
        try {
            assertTrue(env.accept(completeSnapshot()))
            assertTrue(env.trace.indexOf("cancel") < env.trace.indexOf("surface:WORKOUT_COMPLETE"))
            assertTrue(env.trace.indexOf("cache:null") < env.trace.indexOf("surface:WORKOUT_COMPLETE"))
            assertEquals(WearSurfaceKind.WORKOUT_COMPLETE, env.owner.surface.value.kind)
            assertFalse(env.owner.surface.value.hasUnsubmittedDraft)
            assertEquals(OngoingStatus.Inactive, env.owner.ongoingStatus.value)
        } finally {
            collection.cancel()
        }
    }

    @Test
    fun processRestoreIsCanonicalReadOnlyAndDoesNotRepostOngoing() {
        val env = RuntimeTestEnvironment()
        env.accept()
        env.owner.onAction(ControllerAction.SetWeight(null))
        val posts = env.notification.posts.size
        val restored = env.newOwner()
        assertEquals(posts, env.notification.posts.size)
        assertEquals(WearSurfaceKind.REFRESH_REQUIRED, restored.surface.value.kind)
        assertEquals(10_000, restored.surface.value.weightHundredthsKg)
        assertFalse(restored.surface.value.completeEnabled)
        assertFalse(restored.surface.value.hasUnsubmittedDraft)
        assertEquals(WatchActionResult.Rejected, restored.onAction(ControllerAction.CompleteSet))
    }

    @Test
    fun noSessionTombstoneRestoresWithoutInventedIdentityAndExpiresOnWake() {
        val env = RuntimeTestEnvironment()
        env.accept(ReducerTestFixtures.noSession())
        val restored = env.newOwner()
        assertEquals(WearSurfaceKind.NO_SESSION, restored.surface.value.kind)
        env.now += WearProtocol.DISPLAY_CACHE_TTL_MS
        assertEquals(WearSurfaceKind.LOADING, WearSurfaceMapper.map(restored.onWake()).kind)
        assertNull(env.storage.bytes)
    }

    @Test
    fun writeFailureBeforeAtomicPublicationDoesNotPublishCandidate() {
        val env = RuntimeTestEnvironment()
        env.storage.failure = WriteFailure.BEFORE_PUBLISH
        assertThrows(IOException::class.java) { env.accept() }
        assertEquals(WearSurfaceKind.LOADING, env.owner.surface.value.kind)
        assertTrue(env.notification.posts.isEmpty())
        env.storage.failure = null
        assertEquals(WatchActionResult.Rejected, env.owner.onAction(ControllerAction.SetReps(12)))
        assertEquals(WearSurfaceKind.LOADING, env.owner.surface.value.kind)
    }

    @Test
    fun writeFailureAfterAtomicPublicationRecoversDurableSuccessorReadOnly() {
        val matching = ReducerTestFixtures.active(leaseGeneration = 2)
        val active = matching.payload as SnapshotPayload.ActiveWithTarget
        val successors = listOf(
            ReducerTestFixtures.active(revision = 2, leaseGeneration = 2, targetPosition = 1),
            ReducerTestFixtures.active(revision = 2, leaseGeneration = 2),
            matching.copy(payload = active.copy(target = active.target.copy(reps = 12))),
        )
        successors.forEach { successor ->
            val env = RuntimeTestEnvironment()
            env.accept()
            env.owner.onAction(ControllerAction.SetReps(12))
            val oldSurface = env.owner.surface.value
            env.storage.failure = WriteFailure.AFTER_PUBLISH
            assertThrows(IOException::class.java) { env.accept(successor) }
            assertEquals(oldSurface.setOrdinal, env.owner.surface.value.setOrdinal)
            assertEquals(oldSurface.reps, env.owner.surface.value.reps)
            assertFalse(env.owner.surface.value.completeEnabled)
            env.storage.failure = null
            val recovered = WearSurfaceMapper.map(env.owner.onWake())
            val target = (successor.payload as SnapshotPayload.ActiveWithTarget).target
            assertEquals(target.setOrdinal, recovered.setOrdinal)
            assertEquals(target.reps, recovered.reps, "Recovery must reconcile with the durable canonical values")
            assertFalse(recovered.hasUnsubmittedDraft, "Changed source or accepted canonical values clear the draft")
            assertFalse(recovered.completeEnabled)
            assertEquals(WatchActionResult.Rejected, env.owner.onAction(ControllerAction.CompleteSet))
        }
    }

    @Test
    fun notificationFailureAfterPostRequiresRecoveryAndNeverPublishesFreshCandidate() {
        var fail = true
        var permissionReadsBeforeFailure: Int? = null
        val env = RuntimeTestEnvironment { delegate ->
            object : OngoingNotification by delegate {
                override fun post(request: OngoingNotificationRequest): OngoingPostResult {
                    val result = delegate.post(request)
                    if (fail) throw IOException("after platform post")
                    return result
                }

                override fun permissionGranted(): Boolean {
                    if (permissionReadsBeforeFailure == 0) throw IOException("after draft change")
                    permissionReadsBeforeFailure = permissionReadsBeforeFailure?.minus(1)
                    return delegate.permissionGranted()
                }
            }
        }
        assertThrows(IOException::class.java) { env.accept() }
        assertEquals(WearSurfaceKind.LOADING, env.owner.surface.value.kind)
        fail = false
        assertEquals(WearSurfaceKind.REFRESH_REQUIRED, WearSurfaceMapper.map(env.owner.onWake()).kind)
        assertFalse(env.owner.surface.value.completeEnabled)
        assertEquals(1, env.notification.posts.size)
        env.accept(ReducerTestFixtures.active(leaseGeneration = 2))
        env.owner.onAction(ControllerAction.SetWeight(null))
        permissionReadsBeforeFailure = 1
        assertThrows(IOException::class.java) { env.owner.onAction(ControllerAction.SetWeight(5_000)) }
        assertNull(env.owner.surface.value.weightHundredthsKg)
        permissionReadsBeforeFailure = null
        val recovered = WearSurfaceMapper.map(env.owner.onWake())
        assertNull(recovered.weightHundredthsKg, "Recovery must not publish an edit from a failed action")
        assertTrue(recovered.hasUnsubmittedDraft)
        assertFalse(recovered.controlsEnabled)
    }

    @Test
    fun rejectedCorrelationCannotWriteCacheOrOngoing() {
        val env = RuntimeTestEnvironment()
        env.accept()
        env.trace.clear()
        val response = ActiveWorkoutSnapshotResponse(
            WearProtocol.SCHEMA_VERSION,
            ReducerTestFixtures.id(999),
            ReducerTestFixtures.active(revision = 100),
        )
        assertFalse(env.owner.receiveSnapshot(response))
        assertTrue(env.trace.isEmpty())
        assertTrue(env.owner.surface.value.completeEnabled)
    }

    @Test
    fun liveDisplayCacheTtlClearsVisibleValuesBeforeWakeReturns() {
        val env = RuntimeTestEnvironment()
        env.accept()
        env.owner.onAction(ControllerAction.SetWeight(null))
        env.now = 1_000L + WearProtocol.DISPLAY_CACHE_TTL_MS
        val model = WearSurfaceMapper.map(env.owner.onWake())
        assertEquals(WearSurfaceKind.LOADING, model.kind)
        assertNull(model.reps)
        assertFalse(model.hasUnsubmittedDraft)
        assertFalse(model.completeEnabled)
        assertNull(env.storage.bytes)
        assertEquals(OngoingStatus.Inactive, env.owner.ongoingStatus.value)
    }

    @Test
    fun notificationDenialIsPublishedWithoutRemovingValidMutationAuthority() {
        val env = RuntimeTestEnvironment()
        env.notification.allowed = false
        env.accept()
        assertEquals(OngoingStatus.PermissionDenied, env.owner.ongoingStatus.value)
        assertTrue(env.owner.surface.value.completeEnabled)
        env.notification.allowed = true
        env.owner.onWake()
        assertFalse(env.owner.ongoingStatus.value is OngoingStatus.Scheduled)
        assertTrue(env.notification.posts.isEmpty())
    }

    @Test
    fun bootMismatchDropsCachedDisplayAndLease() {
        val env = RuntimeTestEnvironment()
        env.accept()
        env.boot = 2
        val restored = env.newOwner()
        assertEquals(WearSurfaceKind.LOADING, restored.surface.value.kind)
        assertEquals(OngoingStatus.Inactive, restored.ongoingStatus.value)
        assertNull(env.storage.bytes)
    }

    @Test
    fun localeChangeRebuildsFormattedValuesWithoutLosingDraft() {
        val env = RuntimeTestEnvironment()
        env.accept()
        env.owner.onAction(ControllerAction.SetWeight(1_234))
        env.owner.setLocale(Locale.forLanguageTag("ru"))
        assertEquals("12,34", env.owner.surface.value.formattedValues.weight)
        assertTrue(env.owner.surface.value.hasUnsubmittedDraft)
        assertNotNull(env.owner.surface.value.formattedValues.reps)
    }

    @Test
    fun platformStatusCrossingLeaseDeadlineIsReadOnlyBeforeWakeReturns() {
        lateinit var env: RuntimeTestEnvironment
        var readsUntilCrossing: Int? = null
        env = RuntimeTestEnvironment { delegate ->
            object : OngoingNotification by delegate {
                override fun existingDeadlineMs(): Long? {
                    readsUntilCrossing?.let { remaining ->
                        readsUntilCrossing = (remaining - 1).takeIf { it > 0 }
                        if (remaining == 1) env.now = 121_000L
                    }
                    return delegate.existingDeadlineMs()
                }
            }
        }
        env.accept()
        env.now = 120_999L
        // Wake refresh plus two expiry passes precede the final platform-status lookup.
        readsUntilCrossing = 4
        val returned = WearSurfaceMapper.map(env.owner.onWake())
        assertNull(readsUntilCrossing, "The platform status lookup must cross the lease deadline")
        assertEquals(121_000L, env.now)
        assertFalse(returned.controlsEnabled, "Wake must expire editing synchronously before returning")
        assertFalse(returned.completeEnabled, "Wake must expire completion before any scheduled callback")
        assertEquals(CompletionUnavailableReason.REFRESH_REQUIRED, returned.completionUnavailableReason)
        assertEquals(returned, env.owner.surface.value)
        assertEquals(OngoingStatus.Scheduled(126_000L), env.owner.ongoingStatus.value)
        assertEquals(126_000L, env.scheduler.deadline)
        env.now = 126_000L
        env.scheduler.fire()
        assertEquals(OngoingStatus.Inactive, env.owner.ongoingStatus.value)
        assertNull(env.notification.existingDeadlineMs())
    }

    @Test
    fun publicationCrossingCacheTtlSchedulesImmediateInvalidation() {
        val env = RuntimeTestEnvironment()
        env.accept()
        val ttl = 1_000L + WearProtocol.DISPLAY_CACHE_TTL_MS
        env.now = ttl - 1L
        env.owner.onWake()
        val collection = CoroutineScope(Dispatchers.Unconfined).launch {
            env.owner.surface.drop(1).collect { env.now = ttl }
        }
        try {
            env.owner.setLocale(Locale.forLanguageTag("ru"))
            assertEquals(ttl, env.now)
            assertEquals(ttl, env.scheduler.deadline, "An overdue cache boundary must not disappear")
            env.scheduler.fire()
            assertEquals(WearSurfaceKind.LOADING, env.owner.surface.value.kind)
            assertNull(env.owner.surface.value.reps)
            assertNull(env.storage.bytes)
            assertNull(env.scheduler.deadline)
        } finally {
            collection.cancel()
        }
    }

    @Test
    fun disconnectWriteFailurePublishesOnlyPreviousValuesAsReadOnly() {
        WriteFailure.entries.forEach { failure ->
            val env = RuntimeTestEnvironment()
            env.accept()
            env.owner.onAction(ControllerAction.SetReps(12))
            val previous = env.owner.surface.value
            env.now = 2_000L
            env.storage.failure = failure
            assertThrows(IOException::class.java) { env.owner.disconnected() }
            val failed = env.owner.surface.value
            assertEquals(WearSurfaceKind.REFRESH_REQUIRED, failed.kind)
            assertEquals(previous.reps, failed.reps)
            assertEquals(previous.weightHundredthsKg, failed.weightHundredthsKg)
            assertEquals(previous.setOrdinal, failed.setOrdinal)
            assertEquals(previous.hasUnsubmittedDraft, failed.hasUnsubmittedDraft)
            assertFalse(failed.controlsEnabled)
            assertFalse(failed.completeEnabled)
            assertFalse(failed.retryEnabled)
            assertEquals(CompletionUnavailableReason.REFRESH_REQUIRED, failed.completionUnavailableReason)
            assertEquals(OngoingStatus.Inactive, env.owner.ongoingStatus.value)
            assertNull(env.scheduler.deadline)
            assertEquals(7_000L, env.notification.deadline)
            env.storage.failure = null
            val recovered = WearSurfaceMapper.map(env.owner.onWake())
            assertFalse(recovered.controlsEnabled)
            assertEquals(12, recovered.reps, "Same-process recovery must retain the compatible published draft")
            assertTrue(recovered.hasUnsubmittedDraft)
            assertEquals(1, env.notification.posts.size)
        }
    }

    @Test
    fun expiryPlatformFailureCannotLeaveThePreviousEditorEnabled() {
        var failStatus = false
        val env = RuntimeTestEnvironment { delegate ->
            object : OngoingNotification by delegate {
                override fun permissionGranted(): Boolean {
                    if (failStatus) throw IOException("platform status unavailable")
                    return delegate.permissionGranted()
                }
            }
        }
        env.accept()
        env.owner.onAction(ControllerAction.SetWeight(null))
        failStatus = true
        env.now = 121_000L
        assertThrows(IOException::class.java) { env.owner.onWake() }
        assertEquals(WearSurfaceKind.REFRESH_REQUIRED, env.owner.surface.value.kind)
        assertFalse(env.owner.surface.value.controlsEnabled)
        assertFalse(env.owner.surface.value.completeEnabled)
        assertNull(env.owner.surface.value.weightHundredthsKg)
        assertTrue(env.owner.surface.value.hasUnsubmittedDraft)
        assertEquals(OngoingStatus.Inactive, env.owner.ongoingStatus.value)
        assertNull(env.scheduler.deadline)
        assertThrows(IOException::class.java) { env.owner.onWake() }
        failStatus = false
        env.failNextRead = true
        assertThrows(IOException::class.java) { env.owner.onWake() }
        assertNull(env.owner.surface.value.weightHundredthsKg)
        assertTrue(env.owner.surface.value.hasUnsubmittedDraft)
        assertFalse(env.owner.surface.value.controlsEnabled)
        val readsBeforeRecovery = env.reads
        val recovered = WearSurfaceMapper.map(env.owner.onWake())
        assertEquals(readsBeforeRecovery + 1, env.reads, "The next wake must retry the guarded cache read")
        assertEquals(WearSurfaceKind.REFRESH_REQUIRED, recovered.kind)
        assertFalse(recovered.completeEnabled)
        assertNull(recovered.weightHundredthsKg, "Repeated recovery failures must retain explicit null draft weight")
        assertTrue(recovered.hasUnsubmittedDraft)
    }

    @Test
    fun initialRestoreFailureRetainsOneRecoverableReadOnlyOwner() {
        var failStatus = false
        val env = RuntimeTestEnvironment { delegate ->
            object : OngoingNotification by delegate {
                override fun existingDeadlineMs(): Long? {
                    if (failStatus) throw IOException("restore status unavailable")
                    return delegate.existingDeadlineMs()
                }
            }
        }
        env.accept()
        failStatus = true
        val restored = assertDoesNotThrow<WatchRuntimeOwner> { env.newOwner() }
        assertEquals(WearSurfaceKind.LOADING, restored.surface.value.kind)
        assertFalse(restored.surface.value.controlsEnabled)
        assertEquals(OngoingStatus.Inactive, restored.ongoingStatus.value)
        assertNull(env.scheduler.deadline)
        failStatus = false
        assertEquals(WearSurfaceKind.REFRESH_REQUIRED, WearSurfaceMapper.map(restored.onWake()).kind)
        assertEquals(8, restored.surface.value.reps)
        assertFalse(restored.surface.value.completeEnabled)
        assertEquals(1, env.notification.posts.size)
    }

    private fun cachedActive(env: RuntimeTestEnvironment): SnapshotPayload.ActiveWithTarget {
        val record = requireNotNull(CacheFraming.decode(requireNotNull(env.storage.bytes)))
        val decoded = WearProtocolCodec.decodeForWatch(requireNotNull(record.payload)) as WatchDecodeResult.Success
        return (decoded.envelope as ActiveWorkoutSnapshotResponse).snapshot.payload as SnapshotPayload.ActiveWithTarget
    }

    private fun completeSnapshot(): SnapshotData = SnapshotData(
        ReducerTestFixtures.epoch,
        SnapshotPayload.WorkoutComplete(
            ReducerTestFixtures.sessionA,
            2,
            BoundedDisplayName.Value("Training"),
            2,
            2,
        ),
    )
}
