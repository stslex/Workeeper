package io.github.stslex.workeeper.feature.charts.domain.calculator

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.coroutine.asyncMap
import io.github.stslex.workeeper.core.core.utils.NumUiUtils.safeDiv
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.feature.charts.domain.model.ChartDataType
import io.github.stslex.workeeper.feature.charts.domain.model.SingleChartDomainItem
import io.github.stslex.workeeper.feature.charts.domain.model.SingleChartDomainModel
import org.jetbrains.annotations.VisibleForTesting
import javax.inject.Inject

@ViewModelScoped
internal class ChartDomainCalculatorImpl @Inject constructor() : ChartDomainCalculator {

    private var _calculateParams: ChartDomainCalculateParams? = null

    override fun init(
        startTimestamp: Long,
        endTimestamp: Long,
    ) {
        createParams(
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
        )
    }

    private fun getOrCreateParams(
        startTimestamp: Long,
        endTimestamp: Long,
    ): ChartDomainCalculateParams = _calculateParams
        ?.takeIf { startTimestamp == it.start && endTimestamp == it.end }
        ?: createParams(
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
        )

    @Suppress("MagicNumber")
    private fun createParams(
        startTimestamp: Long,
        endTimestamp: Long,
    ): ChartDomainCalculateParams {
        val diffTimestamp = endTimestamp - startTimestamp
        val days = diffTimestamp / DAY_MS
        return when {
            days <= 14 -> ChartDomainCalculateParams(
                xValues = days.toInt(),
                start = startTimestamp,
                end = endTimestamp,
                type = ChartDataType.DAY,
            )

            days <= 60 -> ChartDomainCalculateParams(
                xValues = (days / 7).toInt(),
                start = startTimestamp,
                end = endTimestamp,
                type = ChartDataType.WEEK,
            )

            days <= 365 -> ChartDomainCalculateParams(
                xValues = (days / 30).toInt(),
                start = startTimestamp,
                end = endTimestamp,
                type = ChartDataType.MONTH,
            )

            else -> ChartDomainCalculateParams(
                xValues = (days / 365).toInt(),
                start = startTimestamp,
                end = endTimestamp,
                type = ChartDataType.YEAR,
            )
        }.also {
            _calculateParams = it
        }
    }

    override suspend fun mapTrainings(
        startTimestamp: Long,
        endTimestamp: Long,
        trainings: List<TrainingDataModel>,
        getExercises: suspend (uuids: List<String>) -> List<ExerciseDataModel>,
    ): List<SingleChartDomainModel> {
        val params = getOrCreateParams(
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
        )
        return trainings
            .groupBy { it.name }
            .asyncMap { (name, trainings) ->
                SingleChartDomainModel(
                    name = name,
                    dateType = params.type,
                    values = trainings
                        .asyncMap { training ->
                            val exerciseValue = getExercises(training.exerciseUuids)
                                .map { exercise ->
                                    exercise.sets.maxOfOrNull { it.weight } ?: 0.0
                                }
                                .let { exercise -> exercise.sumOf { it } safeDiv exercise.size }
                                .toFloat()
                            training.timestamp to exerciseValue
                        }
                        .let { items -> mapParams(params, items) },
                )
            }
    }

    override suspend fun mapExercises(
        startTimestamp: Long,
        endTimestamp: Long,
        exercises: List<ExerciseDataModel>,
    ): List<SingleChartDomainModel> {
        val params = getOrCreateParams(
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
        )
        return exercises
            .groupBy { it.name }
            .asyncMap { (name, exercises) ->
                SingleChartDomainModel(
                    name = name,
                    dateType = params.type,
                    values = exercises
                        .map { exercise ->
                            val exerciseValue = exercise.sets
                                .maxOfOrNull { it.weight }
                                ?.toFloat()
                                ?: 0f
                            exercise.timestamp to exerciseValue
                        }
                        .let { items -> mapParams(params, items) },
                )
            }
    }

    private fun mapParams(
        params: ChartDomainCalculateParams,
        items: List<Pair<Long, Float>>,
    ): List<SingleChartDomainItem> = Array(params.xValues) { index ->
        val currentTimeStamp = params.start + params.diffSingle * index

        val startDiff = (currentTimeStamp - params.halfDiffSingle).toLong()
        val endDiff = (currentTimeStamp + params.halfDiffSingle).toLong()

        val searchTimeItem = items.firstOrNull { it.first in startDiff..endDiff }

        SingleChartDomainItem(
            xValue = params.itemDiffK * index,
            yValue = searchTimeItem?.second,
        )
    }.toList()

    @VisibleForTesting
    data class ChartDomainCalculateParams(
        val start: Long,
        val end: Long,
        val xValues: Int,
        val type: ChartDataType,
    ) {

        val diff: Long = end - start

        val diffSingle: Float = if (xValues == 0) {
            0f
        } else {
            diff.toFloat() / xValues
        }

        val halfDiffSingle: Float = diffSingle / 2
        val itemDiffK: Float = when {
            xValues <= 1 -> 0f
            else -> 1f / xValues.dec()
        }
    }

    companion object {

        private const val DAY_MS = 1000 * 60 * 60 * 24
    }
}
