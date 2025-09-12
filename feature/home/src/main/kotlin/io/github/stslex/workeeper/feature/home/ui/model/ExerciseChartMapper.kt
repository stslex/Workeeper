package io.github.stslex.workeeper.feature.home.ui.model

import io.github.stslex.workeeper.core.core.result.Mapping
import io.github.stslex.workeeper.core.exercise.data.model.ExerciseDataModel
import io.github.stslex.workeeper.core.ui.kit.utils.NumUiUtils
import io.github.stslex.workeeper.feature.home.di.HomeScope
import io.github.stslex.workeeper.feature.home.ui.mvi.store.SingleChartUiModel
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Factory
@Scope(HomeScope::class)
@Scoped
internal class ExerciseChartMap(
    private val numUiUtils: NumUiUtils
) : Mapping<List<ExerciseDataModel>, List<SingleChartUiModel>> {

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
                            ?.let { it.weight * (it.reps + it.sets) } ?: 0.0
                    }
                    properties.add(numUiUtils.roundThousand(property))
                }
            } else {
                properties.addAll(
                    items.map {
                        numUiUtils.roundThousand(
                            value = it.weight * (it.reps + it.sets)
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