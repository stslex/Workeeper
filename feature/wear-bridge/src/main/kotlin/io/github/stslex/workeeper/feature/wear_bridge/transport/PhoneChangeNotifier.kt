// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge.transport

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.data.database.wear.WearSyncDao
import io.github.stslex.workeeper.feature.wear_bridge.WatchKnownRevisions
import io.github.stslex.workeeper.feature.wear_bridge.WatchStateKey
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

/**
 * PROVISIONAL, internal testing only (wear-live-sync.md §5.3, D9): the quiet time after the last key
 * change before a signal, so one action that commits in several transactions sends one. Unmeasured.
 */
internal const val CHANGE_SETTLE_MS: Long = 500L

/**
 * PROVISIONAL, internal testing only (§5.3): the minimum time between two signals; changes inside it
 * go out as one signal at its end. Unmeasured.
 */
internal const val CHANGE_MIN_INTERVAL_MS: Long = 2_000L

/** What the change signal needs from the Data Layer, and nothing else (wear-live-sync.md §6.2). */
interface WatchNudgeLink {
    /** Node ids of the reachable watches that advertise WATCH_CAPABILITY. */
    suspend fun reachableWatches(): List<String>

    /** Sends the CHANGED_PATH message, payload 0x00, to one node. */
    suspend fun signal(nodeId: String)
}

/**
 * Tells the paired watches that the active workout may have changed, so they ask again
 * (wear-live-sync.md §6.2, D1). Pure Kotlin: the Data Layer is behind [WatchNudgeLink], and the
 * watches that already hold the current state come from [WatchKnownRevisions] (D7).
 *
 * Armed once per generation by `StartupProcessor.armPostPreflight` on the generation lifetime (§6.3).
 * GUARD: no exception leaves [run] (D11); the Android restore path closes the database under it (F6).
 */
@SingleIn(AppScope::class)
class PhoneChangeNotifier @Inject internal constructor(
    private val wearSyncDao: WearSyncDao,
    private val knownRevisions: WatchKnownRevisions,
    private val link: WatchNudgeLink,
) {

    /** Signals until cancelled, or until the key query fails. */
    suspend fun run() {
        signalChanges(wearSyncDao.activeWearKeys(), knownRevisions, link, Log.tag(LOG_TAG))
    }
}

/**
 * §6.1: the key is exactly the active session's (uuid, wear_revision), or null. GUARD: nothing else
 * may join it. Room re-runs the query on every `session_table` write and a granting handshake writes
 * the lease generation (F2, F3), so a key with any other column would answer every handshake with a
 * signal: an endless phone ↔ watch loop.
 */
internal fun WearSyncDao.activeWearKeys(): Flow<WatchStateKey?> = observeActiveWearKey()
    .map { row -> row?.let { WatchStateKey.of(it.sessionUuid, it.revision) } }

/**
 * §6.2, the notifier's whole behavior. The query re-emits the same key on every `session_table` write,
 * so only a new distinct key is a change, and the generation's first value is a baseline.
 *
 * GUARD: `catch` sits before `conflate`, not after it as §6.2 lists the chain. After `conflate`, a key
 * query that fails while the collector is busy (a lookup, a send, the minimum interval) fails the
 * channel's downstream too, and `catch` then rethrows the failure instead of handling it, so it
 * escapes the notifier (D11). Before `conflate` it handles the failure inside the producer, whose
 * emissions never suspend. The minimum-interval failure test pins this.
 */
@OptIn(FlowPreview::class)
internal suspend fun signalChanges(
    keys: Flow<WatchStateKey?>,
    knownRevisions: WatchKnownRevisions,
    link: WatchNudgeLink,
    logger: Logger,
) {
    keys.distinctUntilChanged()
        .drop(1)
        .debounce(CHANGE_SETTLE_MS)
        .catch { failure -> logger.w { "signal stopped: ${failure::class.simpleName}" } }
        .conflate()
        .collect { key ->
            signalStale(key, knownRevisions, link, logger)
            delay(CHANGE_MIN_INTERVAL_MS)
        }
}

/**
 * Signals every reachable watch whose known key differs from [key], reading the known keys now, when
 * the signal is sent. A node never answered counts as stale. A failed lookup or send is logged by
 * class only and not retried; the next change signals again. GUARD: the log line carries the word
 * `signal`, the result class, the counts and the elapsed time, never a node id (transport §7.9).
 */
private suspend fun signalStale(
    key: WatchStateKey?,
    knownRevisions: WatchKnownRevisions,
    link: WatchNudgeLink,
    logger: Logger,
) {
    val started = TimeSource.Monotonic.markNow()
    var reachable = 0
    var signalled = 0
    var sendFailure: String? = null
    val lookupFailure = attempt {
        val watches = link.reachableWatches()
        reachable = watches.size
        watches.filterNot { node -> knownRevisions.holds(node, key) }.forEach { node ->
            when (val failure = attempt { link.signal(node) }) {
                null -> signalled++
                else -> if (sendFailure == null) sendFailure = failure
            }
        }
    }
    val result = lookupFailure ?: sendFailure ?: RESULT_OK
    logger.i {
        "signal -> $result: $reachable reachable, $signalled signalled, " +
            "${started.elapsedNow().inWholeMilliseconds} ms"
    }
}

/**
 * The failure's class name, or null when [block] succeeded. GUARD: a cancelled Play services Task
 * also surfaces as a CancellationException; only this coroutine's own cancellation propagates.
 */
private suspend fun attempt(block: suspend () -> Unit): String? = runCatching { block() }.fold(
    onSuccess = { null },
    onFailure = { failure ->
        if (failure is CancellationException) currentCoroutineContext().ensureActive()
        failure::class.simpleName ?: "Exception"
    },
)

private const val LOG_TAG = "WearSignal"
private const val RESULT_OK = "ok"
