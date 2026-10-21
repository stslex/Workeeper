// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.usecase

/** Cancels a session: an ad-hoc training cascades, a library one is unlinked. */
interface DeleteSessionUseCase {

    suspend operator fun invoke(sessionUuid: String)
}
