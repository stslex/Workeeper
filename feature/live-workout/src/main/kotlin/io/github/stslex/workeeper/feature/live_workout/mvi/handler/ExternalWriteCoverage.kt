// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExternalSetUiModel

/**
 * The watch writes a load has to apply again (wear-live-sync.md §6.4 "Loads"): a load's read can
 * miss a write the screen received while it ran, and its result would then hide that write.
 *
 * GUARD: confined to the store's main-immediate dispatcher, where every state update of the loads
 * and of the subscription runs; it is never touched from another dispatcher.
 */
internal class ExternalWriteCoverage {

    /** One load. [end] runs for every load, [begin] only if the load got that far. */
    class Load

    private var received = 0L

    /** The loads in flight, each with the count of writes received before its read started. */
    private val marks = HashMap<Load, Long>()
    private val kept = ArrayDeque<KeptWrite>()

    /** Before [load]'s read starts. */
    fun begin(load: Load) {
        marks[load] = received
    }

    /** Every write the subscription receives; kept only while a load is in flight. */
    fun receive(write: ExternalSetUiModel) {
        if (marks.isNotEmpty()) kept.addLast(KeptWrite(received, write))
        received += 1
    }

    /** The writes received after [load] began, in order. */
    fun since(load: Load): List<ExternalSetUiModel> {
        val mark = marks[load] ?: return emptyList()
        return kept.filter { it.index >= mark }.map { it.write }
    }

    /** After [load] ended in any way: applied, empty, failed or cancelled, begun or not. */
    fun end(load: Load) {
        marks.remove(load)
        if (marks.isEmpty()) kept.clear()
    }

    private data class KeptWrite(val index: Long, val write: ExternalSetUiModel)
}
