// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.tags.model.TagDataModel

@Stable
data class TagUiModel(
    val uuid: String,
    val name: String,
)

internal fun TagDataModel.toUi(): TagUiModel = TagUiModel(uuid = uuid, name = name)
