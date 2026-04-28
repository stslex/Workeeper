// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.model

import androidx.compose.runtime.Stable

@Stable
data class TagUiModel(
    val uuid: String,
    val name: String,
)
