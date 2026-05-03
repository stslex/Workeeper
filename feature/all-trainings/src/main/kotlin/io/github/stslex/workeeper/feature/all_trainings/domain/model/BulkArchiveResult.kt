// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.domain.model

internal data class BulkArchiveResult(
    val archivedCount: Int,
    val blockedNames: List<String>,
)
