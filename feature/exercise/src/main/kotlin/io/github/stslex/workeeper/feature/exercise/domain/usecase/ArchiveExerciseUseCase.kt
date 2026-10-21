// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.usecase

import io.github.stslex.workeeper.feature.exercise.domain.model.ArchiveResult

/** Archives the exercise unless an active training still uses it. */
interface ArchiveExerciseUseCase {

    suspend operator fun invoke(uuid: String): ArchiveResult
}
