// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.FingerprintCommand
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandBody
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandResponse
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
import io.github.stslex.workeeper.wear.state.CommandStatus
import io.github.stslex.workeeper.wear.state.LocalMutationAuthority
import io.github.stslex.workeeper.wear.state.RequestToken
import io.github.stslex.workeeper.wear.state.SnapshotReduction
import io.github.stslex.workeeper.wear.state.TargetKey
import io.github.stslex.workeeper.wear.state.WatchDisplayState
import io.github.stslex.workeeper.wear.state.WatchInteractionEligibility
import io.github.stslex.workeeper.wear.state.WatchReducerState
import io.github.stslex.workeeper.wear.state.WatchWorkoutReducer
import io.github.stslex.workeeper.wear.state.WearDraftPolicy
import io.github.stslex.workeeper.wear.state.WorkoutSourceVersion
import io.github.stslex.workeeper.wear.state.openSetOrNull
import io.github.stslex.workeeper.wear.state.safeMonotonicAdd
import io.github.stslex.workeeper.wear.state.sourceVersion
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
    private var selection: WatchSelection? = null
    private var receivedAtMs: Long? = null
    private var tombstone = false
    private var recoveryRequired = false
    private var link = LinkStatus.UNKNOWN
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

    override fun onWake(): WatchRuntimeSnapshot = transition { wake() }.let { snapshot.value }

    override fun setLocale(locale: Locale) = transition {
        selectedLocale = locale
    }

    override fun tileFrame(locale: Locale): WatchTileFrame = synchronized(lock) {
        // A failed wake latches recovery and is logged; the frame still carries what the owner published.
        runWearRuntimeUiEvent {
            transition {
                selectedLocale = locale
                wake()
            }
        }
        WatchTileFrame(
            snapshot = mutableSnapshot.value,
            freshnessIntervalMs = tileFreshnessIntervalMs(nextBoundaryMs(), clock.nowMs(), recoveryRequired),
        )
    }

    private fun wake() {
        // A restored tombstone has no exposed receive time, so its access-time TTL stays at the reader boundary.
        if (tombstone && receivedAtMs == null && cache.read() !is CacheReadResult.NoSession) {
            resetDisplay()
        }
        ongoing.refresh()
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
            acceptLocked(response, reduction, receivedAt, currentBoot)
        }
        reduction.accepted
    }

    /**
     * The phone's answer to a command attempt (wear-paired-transport.md §7.5). An accepted attached
     * snapshot takes the same cache, ongoing and tombstone path as an accepted handshake snapshot.
     * Returns whether that snapshot was accepted, or null when the reducer did not admit the response
     * at all, which the transport treats as no response.
     */
    fun receiveCommandResponse(response: WatchCommandResponse): Boolean? = transition {
        if (response.schemaVersion != WearProtocol.SCHEMA_VERSION) return@transition null
        val currentBoot = bootCount.currentBootCount() ?: return@transition null
        val receivedAt = clock.nowMs()
        val reduction = reducer.receiveCommandResponse(response, receivedAt)
        if (reduction?.accepted == true) {
            val attached = ActiveWorkoutSnapshotResponse(
                schemaVersion = response.schemaVersion,
                correlationId = response.correlationId,
                snapshot = response.replacement,
            )
            acceptLocked(attached, reduction, receivedAt, currentBoot)
        }
        // A response that closed as a protocol mismatch fails closed like protocolFailure: the
        // draft goes and the ongoing surface stops (Phase 1 §8, §9).
        if (reducer.state.display is WatchDisplayState.ProtocolMismatch) {
            pendingDraft = null
            ongoing.displayChanged(reducer.state.display)
        }
        reduction?.accepted
    }

    /** A command attempt got no semantic response in time (§7.3): the reducer's transport timeout. */
    fun transportTimeout(correlationId: CanonicalUuid): Unit = transition {
        reducer.onTransportTimeout(correlationId, clock.nowMs())
    }

    /**
     * The in-flight handshake got no semantic response (§7.5). No reachable phone is the existing
     * disconnect, which retires any attempt binding; a reachable phone that did not answer is a link
     * status only. Neither creates authority, so the handshake's correlation carries nothing to use.
     */
    fun handshakeUnanswered(phoneReachable: Boolean): Unit = transition {
        link = if (phoneReachable) LinkStatus.UNANSWERED else LinkStatus.UNREACHABLE
        if (!phoneReachable) {
            // The existing disconnect, inside this transition.
            reducer.markDisconnected()
            ongoing.disconnected(clock.nowMs())
        }
    }

    /**
     * A response for [correlationId] that failed to decode or had the other operation's shape
     * (§7.5, Phase 1 §9): protocol-mismatch display, authority retired, draft cleared, a pending
     * command closed without retry, and the ongoing surface stopped (Phase 1 §8).
     */
    fun protocolFailure(correlationId: CanonicalUuid): Unit = transition {
        reducer.protocolFailure(correlationId)
        pendingDraft = null
        ongoing.displayChanged(reducer.state.display)
        link = LinkStatus.REACHABLE
    }

    /**
     * The phone's capability is no longer reachable. The link status records it (§7.5), so a display
     * that later expires to Loading shows the retry surface instead of loading forever.
     */
    fun disconnected(): Unit = transition {
        val now = clock.nowMs()
        reducer.markDisconnected()
        ongoing.disconnected(now)
        link = LinkStatus.UNREACHABLE
    }

    private fun acceptLocked(
        response: ActiveWorkoutSnapshotResponse,
        reduction: SnapshotReduction,
        receivedAt: Long,
        currentBoot: Int,
    ) {
        selection = selection.reconciledWith(response.snapshot)
        ongoing.acceptSnapshot(response, reduction, reducer.state, receivedAt, currentBoot)
        receivedAtMs = receivedAt
        tombstone = response.snapshot.payload is SnapshotPayload.NoSession
        reconcileDraft(response.snapshot)
        link = LinkStatus.REACHABLE
    }

    override fun onAction(action: ControllerAction): WatchActionResult = transition {
        val eligibility = if (tombstone) WatchInteractionEligibility() else {
            WatchInteractionEligibility.from(reducer.state.copy(draft = pendingDraft?.values), selection)
        }
        when (action) {
            is ControllerAction.AdjustDraft -> editDraft(eligibility.editing) { draft, exerciseType ->
                when {
                    action.steps == 0 -> null
                    action.field == NumericField.REPS ->
                        draft.copy(reps = WearDraftPolicy.adjustReps(draft.reps, action.steps))
                    exerciseType == ExerciseTypeWire.WEIGHTED -> draft.copy(
                        weightHundredthsKg = WearDraftPolicy.adjustWeight(draft.weightHundredthsKg, action.steps),
                    )
                    else -> null
                }
            }
            is ControllerAction.SetReps -> editDraft(eligibility.editing) { draft, _ ->
                if (action.value in 0..WearProtocol.MAX_WEAR_REPS) draft.copy(reps = action.value) else null
            }
            is ControllerAction.SetWeight -> editDraft(eligibility.editing) { draft, exerciseType ->
                val valid = action.value == null || action.value in 0..WearProtocol.MAX_WEAR_WEIGHT_HUNDREDTHS_KG
                if (valid && exerciseType == ExerciseTypeWire.WEIGHTED) {
                    draft.copy(weightHundredthsKg = action.value)
                } else {
                    null
                }
            }
            ControllerAction.CompleteSet -> reducer.state.completeSetBody(selection, pendingDraft?.values)
                ?.takeIf { eligibility.completion }
                ?.let(::issueCommand)
                ?: WatchActionResult.Rejected
            ControllerAction.Retry -> when {
                eligibility.retry -> reducer.retryCommand(identity.ids.nextId(), clock.nowMs())
                // The link-failure surface (§7.5): a Loading display after a failed handshake refreshes.
                !tombstone && link.failed && reducer.state.display == WatchDisplayState.Loading ->
                    WatchActionResult.RefreshRequested
                else -> WatchActionResult.Rejected
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
        selection = null
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
                selection = selection.reconciledWith(restored.snapshot)
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
        selection = null
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
            link = link,
            selection = selection,
        )
        scheduleNextBoundary()
    }

    /** One edit of the draft on the selected `Open` set, which is then its target (§7.3, P6). */
    private fun editDraft(
        enabled: Boolean,
        change: (CommandDraft, ExerciseTypeWire) -> CommandDraft?,
    ): WatchActionResult {
        reducer.expireAuthority(clock.nowMs())
        if (!enabled || reducer.state.authority !is LocalMutationAuthority.Available) return WatchActionResult.Rejected
        val snapshot = (reducer.state.display as? WatchDisplayState.Active)?.snapshot
            ?: return WatchActionResult.Rejected
        val (exercise, set) = snapshot.selectedOpenSet(selection) ?: return WatchActionResult.Rejected
        val planned = CommandDraft(set.reps, set.weightHundredthsKg)
        val current = pendingDraft?.values ?: planned
        val updated = change(current, exercise.exerciseType) ?: return WatchActionResult.Rejected
        pendingDraft = if (updated == planned) {
            null
        } else {
            PendingDraft(snapshot.sourceVersion(), TargetKey(exercise.performedExerciseUuid, set.position), updated)
        }
        return WatchActionResult.Updated
    }

    /**
     * §7.3: the draft goes when the source or the selected set changes, or when it equals the
     * selected set's planned values.
     */
    private fun reconcileDraft(snapshot: SnapshotData) {
        val pending = pendingDraft ?: return
        val selected = snapshot.selectedOpenSet(selection)
        val sameSourceAndTarget = selected != null && snapshot.sourceVersion() == pending.source &&
            TargetKey(selected.first.performedExerciseUuid, selected.second.position) == pending.target
        if (selected == null || !sameSourceAndTarget ||
            pending.values == CommandDraft(selected.second.reps, selected.second.weightHundredthsKg)
        ) {
            pendingDraft = null
        }
    }

    private fun issueCommand(body: WatchCommandBody): WatchActionResult {
        val active = (reducer.state.display as? WatchDisplayState.Active)?.snapshot
            ?: return WatchActionResult.Rejected
        val payload = active.payload as SnapshotPayload.Active
        val authority = reducer.state.authority as? LocalMutationAuthority.Available
            ?: return WatchActionResult.Rejected
        // §7.6: a command always follows an accepted handshake, which resolved the node id.
        val sourceNodeId = identity.sourceNodeId ?: return WatchActionResult.Rejected
        val fingerprint = FingerprintCommand(
            sourceNodeId = sourceNodeId,
            schemaVersion = WearProtocol.SCHEMA_VERSION,
            commandId = identity.ids.nextId(),
            databaseEpoch = active.databaseEpoch,
            sessionUuid = payload.sessionUuid,
            sessionRevision = payload.sessionRevision,
            body = body,
            mutationLeaseId = authority.leaseId,
            mutationLeaseGeneration = authority.leaseGeneration,
        )
        return when (val issued = reducer.issueCommand(identity.ids.nextId(), clock.nowMs(), fingerprint)) {
            CommandIssueResult.Rejected -> WatchActionResult.Rejected
            is CommandIssueResult.Issued -> WatchActionResult.CommandIssued(issued.token, fingerprint)
        }
    }

    private fun scheduleNextBoundary() {
        // A boundary reached during formatting/platform work still needs its one-shot callback.
        scheduler.replace(nextBoundaryMs()?.coerceAtLeast(clock.nowMs())) { onWake() }
    }

    /** The one deadline source shared by the scheduler and the Tile freshness interval. */
    private fun nextBoundaryMs(): Long? {
        val authority = when (val current = reducer.state.authority) {
            is LocalMutationAuthority.Available -> current.effectiveDeadlineMs
            is LocalMutationAuthority.AttemptBound -> current.effectiveDeadlineMs
            LocalMutationAuthority.Retired -> null
        }
        val ongoingDeadline = (mutableSnapshot.value.ongoing as? OngoingStatus.Scheduled)?.stopAtElapsedRealtimeMs
        val cacheDeadline = receivedAtMs?.let { safeMonotonicAdd(it, WearProtocol.DISPLAY_CACHE_TTL_MS) }
        return listOfNotNull(authority, ongoingDeadline, cacheDeadline).minOrNull()
    }

    private fun newCache() = WatchSnapshotCache(storage, clock, bootCount, OngoingExpiryHandler(notification::cancel))

    private fun newCoordinator() = WatchOngoingCoordinator(cache, clock, notification, policy)

    private data class PendingDraft(val source: WorkoutSourceVersion, val target: TargetKey, val values: CommandDraft)
}

/** `complete_set` for the selected `Open` set: its types from the outline, [draft] or its plan. */
internal fun WatchReducerState.completeSetBody(
    selection: WatchSelection?,
    draft: CommandDraft?,
): WatchCommandBody.CompleteSet? {
    val snapshot = (display as? WatchDisplayState.Active)?.snapshot ?: return null
    val (exercise, set) = snapshot.selectedOpenSet(selection) ?: return null
    val values = draft ?: CommandDraft(set.reps, set.weightHundredthsKg)
    return WatchCommandBody.CompleteSet(
        performedExerciseUuid = exercise.performedExerciseUuid,
        setPosition = set.position,
        reps = values.reps,
        weightHundredthsKg = values.weightHundredthsKg,
        exerciseType = exercise.exerciseType,
        setType = set.setType,
    )
}

/** The selected exercise and its `Open` set on this snapshot's outline, else null. */
internal fun SnapshotData.selectedOpenSet(selection: WatchSelection?) =
    selection?.let { current -> (payload as? SnapshotPayload.Active)?.openSetOrNull(current) }

/**
 * wear-paired-transport.md §7.5: a retryable command issues its one further attempt with its current
 * fingerprint and a new correlation id; a rejected retry asks for a refresh, as before.
 */
private fun WatchWorkoutReducer.retryCommand(correlationId: CanonicalUuid, nowMs: Long): WatchActionResult {
    val issued = when (state.command?.status) {
        CommandStatus.TIMED_OUT_RETRYABLE -> issueTimeoutRetry(correlationId, nowMs)
        CommandStatus.RETRY_READY -> issueTypedRetry(correlationId, nowMs)
        else -> CommandIssueResult.Rejected
    }
    val command = state.command
    return if (issued is CommandIssueResult.Issued && command != null) {
        WatchActionResult.CommandIssued(issued.token, command.fingerprintCommand)
    } else {
        WatchActionResult.RefreshRequested
    }
}
