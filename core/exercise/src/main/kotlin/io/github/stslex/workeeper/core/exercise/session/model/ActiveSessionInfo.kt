// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise.session.model

/**
 * Slim "any session is live" descriptor used by Trainings tab + Training detail to mark
 * the active row and to gate Start-session against an already-running session of another
 * training.
 */
data class ActiveSessionInfo(
    val sessionUuid: String,
    val trainingUuid: String,
    val startedAt: Long,
)
