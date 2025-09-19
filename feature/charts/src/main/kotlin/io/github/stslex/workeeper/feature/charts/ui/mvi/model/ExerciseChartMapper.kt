package io.github.stslex.workeeper.feature.charts.ui.mvi.model

import io.github.stslex.workeeper.core.core.result.Mapper
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.ui.kit.utils.NumUiUtils
import io.github.stslex.workeeper.feature.charts.di.CHARTS_SCOPE_NAME
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scope(name = CHARTS_SCOPE_NAME)
@Scoped
internal class ExerciseChartMap(
    private val numUiUtils: NumUiUtils
) : Mapper<List<ExerciseDataModel>, List<SingleChartUiModel>> {

    // todo refactor to use charts correctly
    override operator fun invoke(data: List<ExerciseDataModel>): List<SingleChartUiModel> {
        val mapOfItems = data
            .groupBy { it.name }
            .mapValues { it.value.distinctBy { data -> data.timestamp } }

        val itemsCount = mapOfItems.values.maxOfOrNull { it.size } ?: 7
        return mapOfItems.map { item ->
            val (name, items) = item
            val minX = items.minOf { it.timestamp }
            val maxX = items.maxOf { it.timestamp }
            val firstTimeStamp = items.firstOrNull()?.timestamp ?: 0L

            val step = (maxX - minX) / itemsCount

            val properties = mutableListOf<Double>()

            if (items.size < itemsCount) {
                repeat(itemsCount) { i ->
                    val startMinX = minX + i * step

                    val property = if (firstTimeStamp < startMinX) {
                        0.0
                    } else {
                        items
                            .firstOrNull { data ->
                                data.timestamp in startMinX..(minX + (i + 1) * step)
                            }
                            ?.let {
                                it.sets.sumOf { it.weight * it.reps }
                            } ?: 0.0
                    }
                    properties.add(numUiUtils.roundThousand(property))
                }
            } else {
                properties.addAll(
                    items.map {
                        numUiUtils.roundThousand(
                            value = it.sets.sumOf { it.weight * it.reps }
                        )
                    }
                )
            }

            SingleChartUiModel(
                name = name,
                properties = properties
            )
        }
    }
}