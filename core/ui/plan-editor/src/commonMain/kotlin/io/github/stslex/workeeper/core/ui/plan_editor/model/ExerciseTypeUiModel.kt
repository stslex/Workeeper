// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.model

import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.feature_exercise_detail_type_weighted
import io.github.stslex.workeeper.core.ui.kit.resources.feature_exercise_detail_type_weightless
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

@Serializable
enum class ExerciseTypeUiModel(
    val labelRes: StringResource,
) {
    WEIGHTED(Res.string.feature_exercise_detail_type_weighted),
    WEIGHTLESS(Res.string.feature_exercise_detail_type_weightless),
}
