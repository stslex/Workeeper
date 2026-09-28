// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.FingerprintCommand
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.wear.cache.AtomicRecordStorage
import io.github.stslex.workeeper.wear.cache.BootCountProvider
import io.github.stslex.workeeper.wear.cache.CacheAbsentReason
import io.github.stslex.workeeper.wear.cache.CacheReadResult
import io.github.stslex.workeeper.wear.cache.CachedConnection
import io.github.stslex.workeeper.wear.cache.ElapsedRealtimeClock
import io.github.stslex.workeeper.wear.cache.OngoingExpiryHandler
import io.github.stslex.workeeper.wear.cache.WatchSnapshotCache
import io.github.stslex.workeeper.wear.ongoing.OngoingNotification
import io.github.stslex.workeeper.wear.ongoing.OngoingPolicy
import io.github.stslex.workeeper.wear.ongoing.OngoingStatus
import io.github.stslex.workeeper.wear.ongoing.WatchOngoingCoordinator
import io.github.stslex.workeeper.wear.state.CommandDraft
import io.github.stslex.workeeper.wear.state.CommandIssueResult
import io.github.stslex.workeeper.wear.state.LocalMutationAuthority
import io.github.stslex.workeeper.wear.state.RequestToken
import io.github.stslex.workeeper.wear.state.TargetKey
import io.github.stslex.workeeper.wear.state.WatchDisplayState
import io.github.stslex.workeeper.wear.state.WatchInteractionEligibility
import io.github.stslex.workeeper.wear.state.WatchWorkoutReducer
import io.github.stslex.workeeper.wear.state.WearDraftPolicy
import io.github.stslex.workeeper.wear.state.WorkoutSourceVersion
import io.github.stslex.workeeper.wear.state.safeMonotonicAdd
import io.github.stslex.workeeper.wear.state.sourceVersion
import io.github.stslex.workeeper.wear.state.targetKeyOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.Locale

/** Owns all reducer, cache and notification writes behind one process-local serialization lock. */
internal class WatchRuntimeOwner(
    private val storage: AtomicRecordStorage,
    private val clock: ElapsedRealtimeClock,
    private val bootCount: BootCountProvider,
    private val notification: OngoingNotification,
    private val policy: OngoingPolicy,
    private val identity: RuntimeIdentity,
    private val scheduler: RuntimeDeadlineScheduler,
    private var selectedLocale: Locale,
) : WatchRuntime {
    private val lock = Any()
    private var reducer = WatchWorkoutReducer()
    private var cache = newCache()
    private var ongoing = newCoordinator()
    private var pendingDraft: PendingDraft? = null
    private var receivedAtMs: Long? = null
    private var tombstone = false
    private var recoveryRequired = false
    private val mutableSnapshot = MutableStateFlow(WatchRuntimeSnapshot(locale = selectedLocale))
    override val snapshot: StateFlow<WatchRuntimeSnapshot> = mutableSnapshot.asStateFlow()

    init {
        restoreOnCreate()
    }

    private fun restoreOnCreate() {
        runCatching { restore() }.onFailure { failure ->
            if (failure !is Exception || !recoveryRequired) throw failure
        }
    }

    fun restore() = synchronized(lock) {
        guarded {
            restoreLocked()
            publish()
        }
    }

    override fun onWake(): WatchRuntimeSnapshot = transition {
        // A restored tombstone has no exposed receive time, so its access-time TTL stays at the reader boundary.
        if (tombstone && receivedAtMs == null && cache.read() !is CacheReadResult.NoSession) {
            resetDisplay()
        }
        ongoing.refresh()
    }.let { snapshot.value }

    override fun setLocale(locale: Locale) = transition {
        selectedLocale = locale
    }

    fun issueHandshake(): RequestToken = transition {
        reducer.issueHandshake(identity.ids.nextId(), clock.nowMs())
    }

    fun receiveSnapshot(response: ActiveWorkoutSnapshotResponse): Boolean = transition {
        if (response.schemaVersion != WearProtocol.SCHEMA_VERSION) return@transition false
        val currentBoot = bootCount.currentBootCount() ?: return@transition false
        val receivedAt = clock.nowMs()
        val reduction = reducer.receiveSnapshot(response.correlationId, response.snapshot, receivedAt)
        if (reduction.accepted) {
            ongoing.acceptSnapshot(response, reduction, reducer.state, receivedAt, currentBoot)
            receivedAtMs = receivedAt
            tombstone = response.snapshot.payload is SnapshotPayload.NoSession
            reconcileDraft(response.snapshot)
        }
        reduction.accepted
    }

    fun disconnected(): Unit = transition {
        val now = clock.nowMs()
        reducer.markDisconnected()
        ongoing.disconnected(now)
        Unit
    }

    override fun onAction(action: ControllerAction): WatchActionResult = transition {
        val eligibility = if (tombstone) WatchInteractionEligibility() else {
            WatchInteractionEligibility.from(reducer.state.copy(draft = pendingDraft?.values))
        }
        when (action) {
            is ControllerAction.AdjustDraft -> editDraft(eligibility.editing) { draft, target ->
                when {
                    action.steps == 0 -> null
                    action.field == NumericField.REPS ->
                        draft.copy(reps = WearDraftPolicy.adjustReps(draft.reps, action.steps))
                    target.exerciseType == ExerciseTypeWire.WEIGHTED -> draft.copy(
                        weightHundredthsKg = WearDraftPolicy.adjustWeight(draft.weightHundredthsKg, action.steps),
                    )
                    else -> null
                }
            }
            is ControllerAction.SetReps -> editDraft(eligibility.editing) { draft, _ ->
                if (action.value in 0..WearProtocol.MAX_WEAR_REPS) draft.copy(reps = action.value) else null
            }
            is ControllerAction.SetWeight -> editDraft(eligibility.editing) { draft, target ->
                val valid = action.value == null || action.value in 0..WearProtocol.MAX_WEAR_WEIGHT_HUNDREDTHS_KG
                if (valid && target.exerciseType == ExerciseTypeWire.WEIGHTED) {
                    draft.copy(weightHundredthsKg = action.value)
                } else {
                    null
                }
            }
            ControllerAction.CompleteSet -> if (eligibility.completion) issueCommand() else WatchActionResult.Rejected
            ControllerAction.Retry -> if (eligibility.retry) {
                WatchActionResult.RefreshRequested
            } else {
                WatchActionResult.Rejected
            }
        }
    }

    private fun <T> transition(block: () -> T): T = synchronized(lock) {
        guarded {
            if (recoveryRequired) restoreLocked()
            checkExpiry()
            val result = block()
            publish()
            result
        }
    }

    private fun <T> guarded(block: () -> T): T {
        val previouslyPublishedDraft = pendingDraft
        return runCatching(block)
            .onFailure { failure ->
                if (failure !is Exception) throw failure
                recoveryRequired = true
                pendingDraft = previouslyPublishedDraft
                publishFailure()
                scheduler.replace(null) {}
            }
            .getOrThrow()
    }

    private fun publishFailure() {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            recoveryRequired = true,
            ongoing = OngoingStatus.Inactive,
        )
    }

    private fun restoreLocked() {
        reducer = WatchWorkoutReducer()
        cache = newCache()
        ongoing = newCoordinator()
        receivedAtMs = null
        tombstone = false
        when (val restored = ongoing.restore()) {
            is CacheReadResult.Absent -> {
                if (restored.reason == CacheAbsentReason.IO_FAILURE) throw IOException("Snapshot cache read failed")
                pendingDraft = null
            }
            CacheReadResult.NoSession -> {
                pendingDraft = null
                tombstone = true
            }
            is CacheReadResult.DisplayOnly -> {
                reducer.restoreDisplayOnly(restored.snapshot)
                reconcileDraft(restored.snapshot)
                if (restored.connection == CachedConnection.DISCONNECTED) reducer.markDisconnected()
                receivedAtMs = restored.receivedAtElapsedRealtimeMs
            }
        }
        recoveryRequired = false
    }

    private fun checkExpiry() {
        val now = clock.nowMs()
        val receivedAt = receivedAtMs
        if (receivedAt != null && (now < receivedAt || now - receivedAt >= WearProtocol.DISPLAY_CACHE_TTL_MS)) {
            // Invalid bytes are rejected/deleted by the same guarded reader used after process restart.
            cache.read()
            resetDisplay()
        }
        reducer.expireAuthority(clock.nowMs())
        val status = ongoing.status()
        val published = mutableSnapshot.value.ongoing as? OngoingStatus.Scheduled
        val scheduledDeadlineReached = published?.let { clock.nowMs() >= it.stopAtElapsedRealtimeMs } == true
        if (status == OngoingStatus.PermissionDenied || scheduledDeadlineReached) {
            ongoing.refresh()
        }
    }

    private fun resetDisplay() {
        reducer = WatchWorkoutReducer()
        pendingDraft = null
        receivedAtMs = null
        tombstone = false
        ongoing.displayChanged(WatchDisplayState.Loading)
    }

    private fun publish() {
        // Atomic cache writes and platform calls can consume the remaining mutation window.
        checkExpiry()
        val status = ongoing.status()
        reducer.expireAuthority(clock.nowMs())
        mutableSnapshot.value = WatchRuntimeSnapshot(
            workout = reducer.state.copy(draft = pendingDraft?.values),
            ongoing = status,
            noSession = tombstone,
            locale = selectedLocale,
        )
        scheduleNextBoundary()
    }

    private fun editDraft(
        enabled: Boolean,
        change: (CommandDraft, io.github.stslex.workeeper.core.wear.protocol.ActiveTarget) -> CommandDraft?,
    ): WatchActionResult {
        reducer.expireAuthority(clock.nowMs())
        if (!enabled || reducer.state.authority !is LocalMutationAuthority.Available) return WatchActionResult.Rejected
        val snapshot = (reducer.state.display as? WatchDisplayState.Active)?.snapshot
            ?: return WatchActionResult.Rejected
        val target = (snapshot.payload as SnapshotPayload.ActiveWithTarget).target
        val current = pendingDraft?.values ?: CommandDraft(target.reps, target.weightHundredthsKg)
        val updated = change(current, target) ?: return WatchActionResult.Rejected
        pendingDraft = if (updated == CommandDraft(target.reps, target.weightHundredthsKg)) {
            null
        } else {
            PendingDraft(snapshot.sourceVersion(), requireNotNull(snapshot.targetKeyOrNull()), updated)
        }
        return WatchActionResult.Updated
    }

    private fun reconcileDraft(snapshot: SnapshotData) {
        val pending = pendingDraft ?: return
        val target = (snapshot.payload as? SnapshotPayload.ActiveWithTarget)?.target
        val sameSourceAndTarget = snapshot.sourceVersion() == pending.source &&
            snapshot.targetKeyOrNull() == pending.target
        if (target == null || !sameSourceAndTarget ||
            pending.values == CommandDraft(target.reps, target.weightHundredthsKg)
        ) {
            pendingDraft = null
        }
    }

    private fun issueCommand(): WatchActionResult {
        val active = (reducer.state.display as? WatchDisplayState.Active)?.snapshot
            ?: return WatchActionResult.Rejected
        val payload = active.payload as SnapshotPayload.ActiveWithTarget
        val authority = reducer.state.authority as? LocalMutationAuthority.Available
            ?: return WatchActionResult.Rejected
        val draft = pendingDraft?.values ?: CommandDraft(payload.target.reps, payload.target.weightHundredthsKg)
        val fingerprint = FingerprintCommand(
            sourceNodeId = identity.sourceNodeId,
            schemaVersion = WearProtocol.SCHEMA_VERSION,
            commandId = identity.ids.nextId(),
            databaseEpoch = active.databaseEpoch,
            sessionUuid = payload.sessionUuid,
            sessionRevision = payload.sessionRevision,
            performedExerciseUuid = payload.target.performedExerciseUuid,
            setPosition = payload.target.setPosition,
            reps = draft.reps,
            weightHundredthsKg = draft.weightHundredthsKg,
            exerciseType = payload.target.exerciseType,
            setType = payload.target.setType,
            mutationLeaseId = authority.leaseId,
            mutationLeaseGeneration = authority.leaseGeneration,
        )
        return when (val issued = reducer.issueCommand(identity.ids.nextId(), clock.nowMs(), fingerprint)) {
            CommandIssueResult.Rejected -> WatchActionResult.Rejected
            is CommandIssueResult.Issued -> WatchActionResult.CommandIssued(issued.token, fingerprint)
        }
    }

    private fun scheduleNextBoundary() {
        val now = clock.nowMs()
        val authority = when (val current = reducer.state.authority) {
            is LocalMutationAuthority.Available -> current.effectiveDeadlineMs
            is LocalMutationAuthority.AttemptBound -> current.effectiveDeadlineMs
            LocalMutationAuthority.Retired -> null
        }
        val ongoingDeadline = (mutableSnapshot.value.ongoing as? OngoingStatus.Scheduled)?.stopAtElapsedRealtimeMs
        val cacheDeadline = receivedAtMs?.let { safeMonotonicAdd(it, WearProtocol.DISPLAY_CACHE_TTL_MS) }
        // A boundary reached during formatting/platform work still needs its one-shot callback.
        val deadline = listOfNotNull(authority, ongoingDeadline, cacheDeadline).minOrNull()?.coerceAtLeast(now)
        scheduler.replace(deadline) { onWake() }
    }

    private fun newCache() = WatchSnapshotCache(storage, clock, bootCount, OngoingExpiryHandler(notification::cancel))

    private fun newCoordinator() = WatchOngoingCoordinator(cache, clock, notification, policy)

    private data class PendingDraft(val source: WorkoutSourceVersion, val target: TargetKey, val values: CommandDraft)
}
