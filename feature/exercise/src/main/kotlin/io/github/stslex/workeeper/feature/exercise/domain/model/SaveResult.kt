// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.model

import kotlin.uuid.Uuid

internal sealed interface SaveResult {

    data class Success(val resolvedUuid: Uuid) : SaveResult

    data object DuplicateName : SaveResult
}
