package io.github.stslex.workeeper.feature.charts.mvi.model

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Suppress("MagicNumber")
internal class ExerciseChartPreviewParameterProvider :
    PreviewParameterProvider<ImmutableList<SingleChartUiModel>> {

    override val values: Sequence<ImmutableList<SingleChartUiModel>> = sequenceOf(
        persistentListOf(
            SingleChartUiModel(
                name = "Test",
                properties = persistentListOf(
                    SingleChartUiProperty(
                        timeX = 0.0f,
                        valueY = 1.0f,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.1f,
                        valueY = 123f,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.2f,
                        valueY = 12f,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.3f,
                        valueY = null,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.4f,
                        valueY = null,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.5f,
                        valueY = 120.0f,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.6f,
                        valueY = 120.0f,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.7f,
                        valueY = 3.0f,
                    ),
                    SingleChartUiProperty(
                        timeX = 1.0f,
                        valueY = 6.0f,
                    ),
                ),
            ),
        ),
        persistentListOf(
            SingleChartUiModel(
                name = "Test",
                properties = persistentListOf(
                    SingleChartUiProperty(
                        timeX = 0.0f,
                        valueY = 0f,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.1f,
                        valueY = 0f,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.2f,
                        valueY = 0f,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.3f,
                        valueY = 0f,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.4f,
                        valueY = 0f,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.5f,
                        valueY = 0f,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.6f,
                        valueY = 0f,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.7f,
                        valueY = 0f,
                    ),
                    SingleChartUiProperty(
                        timeX = 1.0f,
                        valueY = 0f,
                    ),
                ),
            ),
        ),
        persistentListOf(
            SingleChartUiModel(
                name = "Test",
                properties = persistentListOf(
                    SingleChartUiProperty(
                        timeX = 0.0f,
                        valueY = null,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.1f,
                        valueY = null,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.2f,
                        valueY = null,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.3f,
                        valueY = null,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.4f,
                        valueY = null,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.5f,
                        valueY = null,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.6f,
                        valueY = null,
                    ),
                    SingleChartUiProperty(
                        timeX = 0.7f,
                        valueY = null,
                    ),
                    SingleChartUiProperty(
                        timeX = 1.0f,
                        valueY = null,
                    ),
                ),
            ),
        ),
    )

}
