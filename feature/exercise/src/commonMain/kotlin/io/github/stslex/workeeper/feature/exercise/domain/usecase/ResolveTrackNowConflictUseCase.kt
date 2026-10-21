// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.usecase

import io.github.stslex.workeeper.feature.exercise.domain.model.TrackNowConflict

/** Whether an active session blocks Track now, and what it is. */
interface ResolveTrackNowConflictUseCase {

    suspend operator fun invoke(): TrackNowConflict
}
