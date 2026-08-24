// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.data.exercise.session.model.ActiveSessionProgressInfo

/**
 * Central decision helper for "what should happen when the user asks to start a session"
 * given the at-most-one-active-session invariant. Used by every Live-workout entry point —
 * Home Start CTA + picker, Training detail Start session, Exercise detail Track now — so
 * the decision logic and silent-resume case live in one place.
 *
 * Returns one of:
 * - [Resolution.ProceedFresh] — no active session; create a new one.
 * - [Resolution.SilentResume] — active session belongs to the same training; resume it
 *   without prompting the user (they already implicitly chose to continue).
 * - [Resolution.NeedsUserChoice] — active session belongs to a different training; the
 *   caller surfaces the conflict modal and routes the choice through its handler.
 */
// App-Scope Collapse Step 6 (cut): self-bound Metro graph node (no supertype → @SingleIn+@Inject, mirror
// LoggerHolder), exposed as an AppGraph accessor so home + single-training resolve it via
// context.appDeps<T>() post-Hilt. Its sole ctor dep SessionRepository is already @ContributesBinding(AppScope).
@SingleIn(AppScope::class)
@Inject
class SessionConflictResolver(
    private val sessionRepository: SessionRepository,
) {

    suspend fun resolve(requestedTrainingUuid: String): Resolution {
        val active = sessionRepository.getActiveSessionProgress()
            ?: return Resolution.ProceedFresh
        return if (active.trainingUuid == requestedTrainingUuid) {
            Resolution.SilentResume(active.sessionUuid)
        } else {
            Resolution.NeedsUserChoice(active)
        }
    }

    sealed interface Resolution {

        data object ProceedFresh : Resolution

        data class SilentResume(val sessionUuid: String) : Resolution

        data class NeedsUserChoice(val active: ActiveSessionProgressInfo) : Resolution
    }
}
