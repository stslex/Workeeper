package io.github.stslex.workeeper.feature.charts.ui.mvi.model

import androidx.annotation.StringRes
import io.github.stslex.workeeper.feature.charts.R

enum class ChartsType(
    @param:StringRes val labelRes: Int
) {
    TRAINING(
        labelRes = R.string.feature_all_charts_training_type
    ),
    EXERCISE(
        labelRes = R.string.feature_all_charts_exercise_type
    );

    fun onClick(): ChartsType = when (this) {
        TRAINING -> EXERCISE
        EXERCISE -> TRAINING
    }
}