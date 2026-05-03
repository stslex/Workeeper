// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain.model

internal data class TrainingListItemDomain(
    val uuid: String,
    val name: String,
    val exerciseCount: Int,
    val lastSessionAt: Long?,
)
