// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.domain.model

internal sealed interface ArchiveResult {

    data object Success : ArchiveResult

    data class Blocked(val activeTrainings: List<String>) : ArchiveResult
}
