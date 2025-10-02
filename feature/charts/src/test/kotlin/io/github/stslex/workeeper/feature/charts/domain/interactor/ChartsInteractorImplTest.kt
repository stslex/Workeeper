package io.github.stslex.workeeper.feature.charts.domain.interactor

import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.charts.domain.calculator.ChartDomainCalculator
import io.github.stslex.workeeper.feature.charts.domain.model.ChartDataType
import io.github.stslex.workeeper.feature.charts.domain.model.ChartParams
import io.github.stslex.workeeper.feature.charts.domain.model.ChartsDomainType
import io.github.stslex.workeeper.feature.charts.domain.model.SingleChartDomainItem
import io.github.stslex.workeeper.feature.charts.domain.model.SingleChartDomainModel
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChartsInteractorImplTest {

    private val trainingRepository: TrainingRepository = mockk()
    private val exerciseRepository: ExerciseRepository = mockk()
    private val chartsCalculator: ChartDomainCalculator = mockk()
    private val dispatcher: CoroutineDispatcher = StandardTestDispatcher()
    private val chartsInteractor: ChartsInteractor = ChartsInteractorImpl(
        trainingRepository = trainingRepository,
        exerciseRepository = exerciseRepository,
        chartsCalculator = chartsCalculator,
        dispatcher = dispatcher,
    )

    @Test
    fun `getChartsData with TRAINING type returns grouped training data`() =
        runTest(dispatcher) {
            val params = ChartParams(
                startDate = 1000L,
                endDate = 2000L,
                name = "Push",
                type = ChartsDomainType.TRAINING,
            )

            val trainingData = listOf(
                TrainingDataModel(
                    uuid = "training1",
                    name = "Push Day",
                    labels = emptyList(),
                    exerciseUuids = listOf("exercise1", "exercise2"),
                    timestamp = 1500L,
                ),
            )

            val expectedResult = listOf(
                SingleChartDomainModel(
                    name = "Push Day",
                    dateType = ChartDataType.DAY,
                    values = listOf(
                        SingleChartDomainItem(xValue = 0f, yValue = 70.0f),
                    ),
                ),
            )

            coEvery {
                trainingRepository.getTrainings(
                    query = "Push",
                    startDate = 1000L,
                    endDate = 2000L,
                )
            } returns trainingData

            coEvery {
                chartsCalculator.init(
                    startTimestamp = 1000L,
                    endTimestamp = 2000L,
                )
            } just Runs

            coEvery {
                chartsCalculator.mapTrainings(
                    startTimestamp = 1000L,
                    endTimestamp = 2000L,
                    trainings = trainingData,
                    getExercises = any<suspend (List<String>) -> List<ExerciseDataModel>>(),
                )
            } returns expectedResult

            val result = chartsInteractor.getChartsData(params)

            assertEquals(expectedResult, result)

            coVerify(exactly = 1) {
                trainingRepository.getTrainings(
                    query = "Push",
                    startDate = 1000L,
                    endDate = 2000L,
                )
            }
            verify(exactly = 1) {
                chartsCalculator.init(
                    startTimestamp = 1000L,
                    endTimestamp = 2000L,
                )
            }
            coVerify(exactly = 1) {
                chartsCalculator.mapTrainings(
                    startTimestamp = 1000L,
                    endTimestamp = 2000L,
                    trainings = trainingData,
                    getExercises = any<suspend (List<String>) -> List<ExerciseDataModel>>(),
                )
            }
        }

    @Test
    fun `getChartsData with EXERCISE type returns grouped exercise data`() =
        runTest(dispatcher) {
            val params = ChartParams(
                startDate = 1000L,
                endDate = 2000L,
                name = "Push",
                type = ChartsDomainType.EXERCISE,
            )

            val exerciseData = listOf(
                ExerciseDataModel(
                    uuid = "exercise1",
                    name = "Push ups",
                    trainingUuid = null,
                    sets = emptyList(),
                    labels = emptyList(),
                    timestamp = 1500L,
                ),
            )

            val expectedResult = listOf(
                SingleChartDomainModel(
                    name = "Push ups",
                    dateType = ChartDataType.DAY,
                    values = listOf(
                        SingleChartDomainItem(xValue = 0f, yValue = 60.0f),
                    ),
                ),
            )

            coEvery {
                exerciseRepository.getExercises(
                    name = "Push",
                    startDate = 1000L,
                    endDate = 2000L,
                )
            } returns exerciseData

            coEvery {
                chartsCalculator.init(
                    startTimestamp = 1000L,
                    endTimestamp = 2000L,
                )
            } just Runs

            coEvery {
                chartsCalculator.mapExercises(
                    startTimestamp = 1000L,
                    endTimestamp = 2000L,
                    exercises = exerciseData,
                )
            } returns expectedResult

            val result = chartsInteractor.getChartsData(params)

            assertEquals(expectedResult, result)

            coVerify(exactly = 1) {
                exerciseRepository.getExercises(
                    name = "Push",
                    startDate = 1000L,
                    endDate = 2000L,
                )
            }
            verify(exactly = 1) {
                chartsCalculator.init(startTimestamp = 1000L, endTimestamp = 2000L)
            }
            coVerify(exactly = 1) {
                chartsCalculator.mapExercises(
                    startTimestamp = 1000L,
                    endTimestamp = 2000L,
                    exercises = exerciseData,
                )
            }
        }

    @Test
    fun `getChartsData with TRAINING type handles empty training list`() = runTest(dispatcher) {
        val params = ChartParams(
            startDate = 1000L,
            endDate = 2000L,
            name = "NonExistent",
            type = ChartsDomainType.TRAINING,
        )

        coEvery {
            trainingRepository.getTrainings(
                query = "NonExistent",
                startDate = 1000L,
                endDate = 2000L,
            )
        } returns emptyList()

        coEvery {
            chartsCalculator.init(
                startTimestamp = 1000L,
                endTimestamp = 2000L,
            )
        } just Runs

        coEvery {
            chartsCalculator.mapTrainings(
                startTimestamp = 1000L,
                endTimestamp = 2000L,
                trainings = emptyList(),
                getExercises = any<suspend (List<String>) -> List<ExerciseDataModel>>(),
            )
        } returns emptyList()

        val result = chartsInteractor.getChartsData(params)

        assertEquals(emptyList<SingleChartDomainModel>(), result)
        coVerify(exactly = 1) {
            trainingRepository.getTrainings(
                query = "NonExistent",
                startDate = 1000L,
                endDate = 2000L,
            )
        }
        verify(exactly = 1) {
            chartsCalculator.init(startTimestamp = 1000L, endTimestamp = 2000L)
        }
        coVerify(exactly = 1) {
            chartsCalculator.mapTrainings(
                startTimestamp = 1000L,
                endTimestamp = 2000L,
                trainings = emptyList(),
                getExercises = any<suspend (List<String>) -> List<ExerciseDataModel>>(),
            )
        }
    }

    @Test
    fun `getChartsData with EXERCISE type handles empty exercise list`() = runTest(dispatcher) {
        val params = ChartParams(
            startDate = 1000L,
            endDate = 2000L,
            name = "NonExistent",
            type = ChartsDomainType.EXERCISE,
        )

        coEvery {
            exerciseRepository.getExercises(
                name = "NonExistent",
                startDate = 1000L,
                endDate = 2000L,
            )
        } returns emptyList()

        coEvery {
            chartsCalculator.init(
                startTimestamp = 1000L,
                endTimestamp = 2000L,
            )
        } just Runs

        coEvery {
            chartsCalculator.mapExercises(
                startTimestamp = 1000L,
                endTimestamp = 2000L,
                exercises = emptyList(),
            )
        } returns emptyList()

        val result = chartsInteractor.getChartsData(params)

        assertEquals(emptyList<SingleChartDomainModel>(), result)
        coVerify(exactly = 1) {
            exerciseRepository.getExercises(
                name = "NonExistent",
                startDate = 1000L,
                endDate = 2000L,
            )
        }
        verify(exactly = 1) {
            chartsCalculator.init(
                startTimestamp = 1000L,
                endTimestamp = 2000L,
            )
        }
        coVerify(exactly = 0) {
            chartsCalculator.mapTrainings(
                startTimestamp = 1000L,
                endTimestamp = 2000L,
                trainings = emptyList(),
                getExercises = any<suspend (List<String>) -> List<ExerciseDataModel>>(),
            )
        }
    }
}
