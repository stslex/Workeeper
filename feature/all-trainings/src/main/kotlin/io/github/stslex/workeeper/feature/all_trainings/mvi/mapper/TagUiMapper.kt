// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.mvi.mapper

import io.github.stslex.workeeper.core.data.exercise.tags.model.TagDataModel
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TagUiModel

internal fun TagDataModel.toUi(): TagUiModel = TagUiModel(uuid = uuid, name = name)
