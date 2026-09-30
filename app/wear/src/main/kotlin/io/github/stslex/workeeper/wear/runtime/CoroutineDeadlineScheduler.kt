// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.wear.cache.ElapsedRealtimeClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One replaceable absolute deadline on a coroutine delay; never a poll or a wake lock. Shared by the
 * debug and release factories (wear-paired-transport.md §7.7).
 */
internal class CoroutineDeadlineScheduler(private val clock: ElapsedRealtimeClock) : RuntimeDeadlineScheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var deadlineJob: Job? = null

    override fun replace(deadlineElapsedRealtimeMs: Long?, callback: () -> Unit) {
        deadlineJob?.cancel()
        deadlineJob = deadlineElapsedRealtimeMs?.let { deadline ->
            scope.launch {
                delay((deadline - clock.nowMs()).coerceAtLeast(0L))
                runWearRuntimeUiEvent(callback)
            }
        }
    }
}
