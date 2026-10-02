// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One set that a writer outside the live-workout screen (the paired watch, through the phone bridge)
 * committed, with the values exactly as written (wear-live-sync.md §6.4).
 */
data class ExternalSetWrite(
    val sessionUuid: String,
    val performedExerciseUuid: String,
    val position: Int,
    val weight: Double?,
    val reps: Int,
    val type: SetsDataType,
)

/**
 * The in-process signal from the phone bridge to an open live-workout screen (wear-live-sync.md
 * §6.4, D4): the screen patches the set in place instead of re-reading the session. In memory only;
 * a write nobody collects is gone, and the next load shows the database.
 */
@SingleIn(AppScope::class)
class ExternalSetWrites @Inject constructor() {

    private val shared = MutableSharedFlow<ExternalSetWrite>(replay = 0, extraBufferCapacity = BUFFER)

    /** GUARD: a hot flow with no operator between it and the collector, so a subscription is synchronous. */
    val writes: Flow<ExternalSetWrite> = shared.asSharedFlow()

    /** Never suspends; a write the buffer refuses is dropped and logged by class only. */
    fun publish(write: ExternalSetWrite) {
        if (!shared.tryEmit(write)) logger.w { "external set write refused: ${write::class.simpleName}" }
    }

    private companion object {
        const val BUFFER = 64
        val logger = Log.tag("ExternalSetWrites")
    }
}
