package io.github.stslex.workeeper.feature.home.ui.model

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.stslex.workeeper.feature.home.ui.mvi.store.SingleChartUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

class ExerciseChartPreviewParameterProvider :
    PreviewParameterProvider<ImmutableList<SingleChartUiModel>> {

    override val values: Sequence<ImmutableList<SingleChartUiModel>>
        get() = sequence {
            persistentListOf(
                SingleChartUiModel(
                    name = "Test",
                    properties = listOf(
                        1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0
                    ),
                ),

                SingleChartUiModel(
                    name = "Test",
                    properties = listOf(
                        12.0, 2.0, 5.0, 4.0, 1.0, 6.0, 7.0
                    ),
                )
            )
        }

}