// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.domain.model

internal data class TrainingChangeDomain(
    val uuid: String,
    val name: String,
    val description: String?,
    val isAdhoc: Boolean,
    val archived: Boolean,
    val timestamp: Long,
    val labels: List<String>,
    val exerciseUuids: List<String>,
)
