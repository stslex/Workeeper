package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import io.github.stslex.workeeper.feature.exercise.R

enum class SetType(
    @param:StringRes val stringRes: Int,
    val color: Color
) {
    WARM(
        stringRes = R.string.feature_exercise_set_type_warm,
        color = Color(0xFF6CB2F5)
    ),
    WORK(
        stringRes = R.string.feature_exercise_set_type_work,
        color = Color(0xFF83C786)
    ),
    FAIL(
        stringRes = R.string.feature_exercise_set_type_fail,
        color = Color(0xFFFCA29D)
    ),
    DROP(
        stringRes = R.string.feature_exercise_set_type_drop,
        color = Color(0xFF9C7DD0)
    )
}