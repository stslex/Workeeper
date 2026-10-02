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

    private var received = 0L
    private var loadsInFlight = 0
    private val kept = ArrayDeque<KeptWrite>()

    /** Before a load's read: the mark that [since] takes when this load's result is applied. */
    fun beginLoad(): Long {
        loadsInFlight += 1
        return received
    }

    /** Every write the subscription receives; kept only while a load is in flight. */
    fun receive(write: ExternalSetUiModel) {
        if (loadsInFlight > 0) kept.addLast(KeptWrite(received, write))
        received += 1
    }

    /** The writes received after [mark], in order. */
    fun since(mark: Long): List<ExternalSetUiModel> = kept.filter { it.index >= mark }.map { it.write }

    /** After a load ended in any way, applied, empty, failed or cancelled. */
    fun endLoad() {
        loadsInFlight -= 1
        if (loadsInFlight == 0) kept.clear()
    }

    private data class KeptWrite(val index: Long, val write: ExternalSetUiModel)
}
