// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * O6's own bookkeeping (wear-live-sync.md §7.3): whether a phone change is pending, the token bucket
 * that limits O6 instead of [AUTO_REFRESH_BUDGET], and the one deferral. The coordinator owns it and
 * calls it on its single-threaded scope only, so nothing here is synchronized.
 */
internal class PhoneChangeLimiter(
    private val scope: CoroutineScope,
    private val burst: Int = PHONE_CHANGE_BURST,
    private val refillMs: Long = PHONE_CHANGE_REFILL_MS,
) {
    /** Rule 1: set by a signal; rule 2: cleared when any handshake token is issued. */
    var pending: Boolean = false
        private set

    private var tokens = burst

    /** When the refill period now running started; null while the bucket is full. */
    private var refillFromMs: Long? = null

    private var deferral: Job? = null

    fun signal() {
        pending = true
    }

    /** The change was served, or given up: nothing is pending and no deferral stays armed (rule 5). */
    fun clear() {
        pending = false
        deferral?.cancel()
        deferral = null
    }

    /** Milliseconds until the bucket holds a token at [nowMs]; 0 when it holds one. */
    fun msUntilToken(nowMs: Long): Long {
        refill(nowMs)
        val from = refillFromMs
        return if (tokens > 0 || from == null) 0L else (from + refillMs - nowMs).coerceAtLeast(1L)
    }

    /** Takes one token. GUARD: only after [msUntilToken] returned 0 for the same [nowMs]. */
    fun take(nowMs: Long) {
        refill(nowMs)
        check(tokens > 0) { "phone change bucket is empty" }
        if (tokens == burst) refillFromMs = nowMs
        tokens -= 1
    }

    /**
     * Rule 5: arms the one deferral, replacing an armed one, to call [serve] once after [delayMs].
     * It is the coordinator's only timer and only delays an O6 that already arrived.
     */
    fun defer(delayMs: Long, serve: () -> Unit) {
        deferral?.cancel()
        deferral = scope.launch {
            delay(delayMs)
            deferral = null
            serve()
        }
    }

    private fun refill(nowMs: Long) {
        val from = refillFromMs ?: return
        val regained = (nowMs - from) / refillMs
        if (regained <= 0L) return
        tokens = (tokens + regained).coerceAtMost(burst.toLong()).toInt()
        refillFromMs = if (tokens == burst) null else from + regained * refillMs
    }
}
