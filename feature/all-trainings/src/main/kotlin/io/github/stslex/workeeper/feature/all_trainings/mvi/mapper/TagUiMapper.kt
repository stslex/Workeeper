// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.mvi.mapper

import io.github.stslex.workeeper.feature.all_trainings.domain.model.TagDomain
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TagUiModel

internal fun TagDomain.toUi(): TagUiModel = TagUiModel(uuid = uuid, name = name)
