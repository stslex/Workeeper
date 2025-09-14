package io.github.stslex.workeeper.feature.exercise.ui.mvi.model

import androidx.annotation.StringRes
import io.github.stslex.workeeper.feature.exercise.R

enum class PropertyType(
    @param:StringRes val stringRes: Int,
) {
    NAME(
        stringRes = R.string.feature_exercise_field_label_name
    ),
    REPS(
        stringRes = R.string.feature_exercise_field_label_reps
    ),
    WEIGHT(
        stringRes = R.string.feature_exercise_field_label_weight
    );
}
