// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.domain.model

sealed interface StartSessionConflict {

    data object ProceedFresh : StartSessionConflict

    data class SilentResume(val sessionUuid: String) : StartSessionConflict

    data class NeedsUserChoice(val active: ActiveSessionDomain) : StartSessionConflict
}
