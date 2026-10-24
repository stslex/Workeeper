// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.usecase

/** Starts an ad-hoc session for one exercise and returns its uuid. */
interface StartTrackNowSessionUseCase {

    suspend operator fun invoke(
        exerciseUuid: String,
        defaultName: String,
    ): String
}
