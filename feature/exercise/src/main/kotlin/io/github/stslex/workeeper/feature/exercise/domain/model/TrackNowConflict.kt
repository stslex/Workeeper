// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.model

internal sealed interface TrackNowConflict {

    data object ProceedFresh : TrackNowConflict

    data class NeedsUserChoice(
        val active: ActiveSessionDomain,
        val trainingName: String?,
    ) : TrackNowConflict
}
