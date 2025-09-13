package io.github.stslex.workeeper.feature.home.ui.model

import androidx.annotation.StringRes
import io.github.stslex.workeeper.feature.home.R

internal enum class HomeTabs(
    @param:StringRes val titleRes: Int
) {

    ALL(
        titleRes = R.string.home_tab_exercises
    ),
    CHARTS(
        titleRes = R.string.home_tab_charts
    )
}