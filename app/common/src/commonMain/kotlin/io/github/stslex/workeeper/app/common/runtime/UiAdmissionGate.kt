// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app.common.runtime

import io.github.stslex.workeeper.app.common.di.AppUiAdmissionToken
import io.github.stslex.workeeper.core.core.logger.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withTimeoutOrNull

/** Atomically observes zero UI grants and retires a generation. */
class UiAdmissionGate(private val logger: Logger) {

    private class Grant(val generationId: Int) : AppUiAdmissionToken

    private data class State(
        val live: Map<Int, Set<Grant>> = emptyMap(),
        val retired: Set<Int> = emptySet(),
    )

    private val state = MutableStateFlow(State())

    /** Requests composition-time admission; a retired generation receives no token. */
    fun admit(generationId: Int): AppUiAdmissionToken? {
        val grant = Grant(generationId)
        // Membership in the winning CAS state determines admission, even after a retry.
        val after = state.updateAndGet { s ->
            if (generationId in s.retired) {
                s
            } else {
                s.copy(live = s.live + (generationId to (s.live[generationId].orEmpty() + grant)))
            }
        }
        if (grant !in after.live[generationId].orEmpty()) {
            logger.w {
                "ui admission REFUSED for retired generation $generationId — the region renders nothing"
            }
            return null
        }
        return grant
    }

    /** Releases this exact grant; foreign, repeated and stale releases are no-ops. */
    fun release(token: AppUiAdmissionToken) {
        val grant = token as? Grant ?: return
        state.update { s ->
            val grants = s.live[grant.generationId] ?: return@update s
            if (grant !in grants) return@update s
            val remaining = grants - grant
            s.copy(
                live = if (remaining.isEmpty()) {
                    s.live - grant.generationId
                } else {
                    s.live + (grant.generationId to remaining)
                },
            )
        }
    }

    /** Waits for zero admissions and atomically retires the id. */
    suspend fun awaitRetired(id: Int, timeoutMillis: Long): Boolean {
        // The deadline can fire after the CAS but before the timed block returns.
        var retired = false
        withTimeoutOrNull(timeoutMillis) {
            while (!retired) {
                state.first { s -> s.live[id].isNullOrEmpty() || id in s.retired }
                val after = state.updateAndGet { s ->
                    if (s.live[id].isNullOrEmpty() && id !in s.retired) {
                        s.copy(live = s.live - id, retired = s.retired + id)
                    } else {
                        s
                    }
                }
                retired = id in after.retired
            }
        }
        return retired
    }

    /** Reopens admission for an aborted transition's outgoing id. */
    fun reopen(id: Int) {
        state.update { s -> s.copy(retired = s.retired - id) }
    }

    fun admittedCount(id: Int): Int = state.value.live[id].orEmpty().size
}
