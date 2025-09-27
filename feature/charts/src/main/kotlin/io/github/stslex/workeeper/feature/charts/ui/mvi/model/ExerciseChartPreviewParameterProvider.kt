package io.github.stslex.workeeper.feature.charts.ui.mvi.model

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

@Suppress("MagicNumber")
internal class ExerciseChartPreviewParameterProvider :
    PreviewParameterProvider<ImmutableList<SingleChartUiModel>> {

    override val values: Sequence<ImmutableList<SingleChartUiModel>> = persistentListOf(
        SingleChartUiModel(
            name = "Test",
            properties = persistentListOf(
                SingleChartUiProperty(
                    timeX = 0.0f,
                    valueY = 1.0f,
                ),
                SingleChartUiProperty(
                    timeX = 0.2f,
                    valueY = 2.0f,
                ),
                SingleChartUiProperty(
                    timeX = 0.4f,
                    valueY = 5.0f,
                ),
                SingleChartUiProperty(
                    timeX = 0.6f,
                    valueY = 120.0f,
                ),
                SingleChartUiProperty(
                    timeX = 0.8f,
                    valueY = 3.0f,
                ),
                SingleChartUiProperty(
                    timeX = 1.0f,
                    valueY = 6.0f,
                ),
            ),
        ),
    ).toSequence()

    private fun PersistentList<SingleChartUiModel>.toSequence(): Sequence<ImmutableList<SingleChartUiModel>> =
        sequenceOf(this)
}
