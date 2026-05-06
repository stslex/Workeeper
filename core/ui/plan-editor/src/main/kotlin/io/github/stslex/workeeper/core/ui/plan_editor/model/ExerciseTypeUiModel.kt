// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.model

import io.github.stslex.workeeper.core.ui.kit.R

enum class ExerciseTypeUiModel(
    val labelRes: Int,
) {
    WEIGHTED(R.string.feature_exercise_detail_type_weighted),
    WEIGHTLESS(R.string.feature_exercise_detail_type_weightless),
}
