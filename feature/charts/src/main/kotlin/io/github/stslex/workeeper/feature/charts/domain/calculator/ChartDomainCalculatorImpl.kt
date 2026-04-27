package io.github.stslex.workeeper.feature.charts.domain.calculator

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.coroutine.asyncMap
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
                            // v3 reshape: max-set-weight aggregation lives on session sets,
                            // not on the exercise template. Until charts are reimplemented
                            // against the session/set tables, surface 0 here.
                            @Suppress("UNUSED_VARIABLE")
                            val resolved = getExercises(training.exerciseUuids)
                            training.timestamp to 0f
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
                            // See mapTrainings: sets live on session/SetEntity in v3, so
                            // until charts are reimplemented this surfaces 0.
                            exercise.timestamp to 0f
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
    }
        .let { points ->
            val nullableFirstX = mutableListOf<Float>()
            val nullableLastX = mutableListOf<Float>()

            points.forEach { point ->
                if (point.yValue == null) {
                    nullableFirstX.add(point.xValue)
                    nullableLastX.add(point.xValue)
                } else {
                    nullableLastX.clear()
                }
            }
            points.map { point ->
                val nullableFirst = nullableFirstX.contains(point.xValue)
                val nullableLast = nullableLastX.contains(point.xValue)
                if (nullableLast || nullableFirst) {
                    point.copy(yValue = 0f)
                } else {
                    point
                }
            }
        }
        .toList()

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
