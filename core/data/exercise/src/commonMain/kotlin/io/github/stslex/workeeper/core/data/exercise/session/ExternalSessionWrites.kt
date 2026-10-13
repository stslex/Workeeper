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
 * One write that a writer outside the live-workout screen (the paired watch, through the phone
 * bridge) committed to the active session (wear-live-sync.md §6.4, wear-phase-2-exercise-list.md §6.5).
 */
sealed interface ExternalSessionWrite {
    val sessionUuid: String
    val performedExerciseUuid: String

    /** One set, with the values exactly as written. */
    data class SetMarked(
        override val sessionUuid: String,
        override val performedExerciseUuid: String,
        val position: Int,
        val weight: Double?,
        val reps: Int,
        val type: SetsDataType,
    ) : ExternalSessionWrite {

        /** GUARD: names its type only; the values are never logged (wear-phase-2-exercise-list.md §9). */
        override fun toString(): String = "ExternalSessionWrite.SetMarked"
    }

    /** An exercise skipped ([skipped] true) or returned to the session (false); the flag only. */
    data class SkipChanged(
        override val sessionUuid: String,
        override val performedExerciseUuid: String,
        val skipped: Boolean,
    ) : ExternalSessionWrite
}

/**
 * The in-process signal from the phone bridge to an open live-workout screen (wear-live-sync.md
 * §6.4, D4): the screen patches the set or the exercise in place instead of re-reading the session.
 * In memory only; a write nobody collects is gone, and the next load shows the database.
 */
@SingleIn(AppScope::class)
class ExternalSessionWrites @Inject constructor() {

    private val shared = MutableSharedFlow<ExternalSessionWrite>(replay = 0, extraBufferCapacity = BUFFER)

    /** GUARD: a hot flow with no operator between it and the collector, so a subscription is synchronous. */
    val writes: Flow<ExternalSessionWrite> = shared.asSharedFlow()

    /** Never suspends; a write the buffer refuses is dropped and logged by class only. */
    fun publish(write: ExternalSessionWrite) {
        if (!shared.tryEmit(write)) logger.w { "external session write refused: ${write::class.simpleName}" }
    }

    private companion object {
        const val BUFFER = 64
        val logger = Log.tag("ExternalSessionWrites")
    }
}
