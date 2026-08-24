// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.data.exercise.session.model.ActiveSessionProgressInfo

/**
 * Decides how a start-session request resolves against the at-most-one-active-session invariant:
 * proceed fresh, silently resume the same training, or hand the conflict to the caller's modal.
 */
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
