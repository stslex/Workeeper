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
                1.0f,
                2.0f,
                3.0f,
                4.0f,
                5.0f,
                6.0f,
                7.0f,
            ),
        ),

        SingleChartUiModel(
            name = "Test",
            properties = persistentListOf(
                12.0f,
                2.0f,
                5.0f,
                4.0f,
                1.0f,
                6.0f,
                7.0f,
            ),
        ),
    ).toSequence()

    private fun PersistentList<SingleChartUiModel>.toSequence(): Sequence<ImmutableList<SingleChartUiModel>> =
        sequenceOf(this)
}
