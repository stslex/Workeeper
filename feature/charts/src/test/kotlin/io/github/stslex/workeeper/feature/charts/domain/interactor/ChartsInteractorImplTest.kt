package io.github.stslex.workeeper.feature.charts.domain.interactor

import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.charts.domain.model.ChartParams
import io.github.stslex.workeeper.feature.charts.domain.model.ChartsDomainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ChartsInteractorImplTest {

    private val trainingRepository: TrainingRepository = mockk()
    private val exerciseRepository: ExerciseRepository = mockk()
    private val dispatcher: CoroutineDispatcher = StandardTestDispatcher()
    private val chartsInteractor: ChartsInteractor = ChartsInteractorImpl(
        trainingRepository = trainingRepository,
        exerciseRepository = exerciseRepository,
        dispatcher = dispatcher,
    )

    @Test
    fun `getChartsData with TRAINING type returns grouped training data with average max weights`() =
        runTest(dispatcher) {
            // Given
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
                TrainingDataModel(
                    uuid = "training2",
                    name = "Push Day",
                    labels = emptyList(),
                    exerciseUuids = listOf("exercise3"),
                    timestamp = 1800L,
                ),
                TrainingDataModel(
                    uuid = "training3",
                    name = "Pull Day",
                    labels = emptyList(),
                    exerciseUuids = listOf("exercise4"),
                    timestamp = 1700L,
                ),
            )

            val exercise1 = ExerciseDataModel(
                uuid = "exercise1",
                name = "Push ups",
                trainingUuid = "training1",
                sets = listOf(
                    SetsDataModel(Uuid.random().toString(), 10, 50.0, SetsDataType.WORK),
                    SetsDataModel(Uuid.random().toString(), 8, 60.0, SetsDataType.WORK),
                ),
                labels = emptyList(),
                timestamp = 1500L,
            )

            val exercise2 = ExerciseDataModel(
                uuid = "exercise2",
                name = "Bench press",
                trainingUuid = "training1",
                sets = listOf(
                    SetsDataModel(Uuid.random().toString(), 5, 80.0, SetsDataType.WORK),
                ),
                labels = emptyList(),
                timestamp = 1500L,
            )

            val exercise3 = ExerciseDataModel(
                uuid = "exercise3",
                name = "Dips",
                trainingUuid = "training2",
                sets = listOf(
                    SetsDataModel(Uuid.random().toString(), 12, 40.0, SetsDataType.WORK),
                ),
                labels = emptyList(),
                timestamp = 1800L,
            )

            val exercise4 = ExerciseDataModel(
                uuid = "exercise4",
                name = "Pull ups",
                trainingUuid = "training3",
                sets = listOf(
                    SetsDataModel(Uuid.random().toString(), 8, 70.0, SetsDataType.WORK),
                ),
                labels = emptyList(),
                timestamp = 1700L,
            )

            coEvery {
                trainingRepository.getTrainings(
                    query = "Push",
                    startDate = 1000L,
                    endDate = 2000L,
                )
            } returns trainingData

            coEvery {
                exerciseRepository.getExercisesByUuid(
                    listOf(
                        "exercise1",
                        "exercise2",
                    ),
                )
            } returns listOf(exercise1, exercise2)
            coEvery { exerciseRepository.getExercisesByUuid(listOf("exercise3")) } returns listOf(
                exercise3,
            )
            coEvery { exerciseRepository.getExercisesByUuid(listOf("exercise4")) } returns listOf(
                exercise4,
            )

            // When
            val result = chartsInteractor.getChartsData(params)

            // Then
            assertEquals(2, result.size)

            val pushDayChart = result.find { it.name == "Push Day" }!!
            assertEquals(2, pushDayChart.values.size)

            // First training: (60.0 + 80.0) / 2 = 70.0
            val firstTraining = pushDayChart.values.find { it.timestamp == 1500L }!!
            assertEquals(70.0f, firstTraining.value)

            // Second training: 40.0 / 1 = 40.0
            val secondTraining = pushDayChart.values.find { it.timestamp == 1800L }!!
            assertEquals(40.0f, secondTraining.value)

            val pullDayChart = result.find { it.name == "Pull Day" }!!
            assertEquals(1, pullDayChart.values.size)
            assertEquals(70.0f, pullDayChart.values[0].value)
            assertEquals(1700L, pullDayChart.values[0].timestamp)

            coVerify(exactly = 1) {
                trainingRepository.getTrainings(
                    query = "Push",
                    startDate = 1000L,
                    endDate = 2000L,
                )
            }
            coVerify(exactly = 1) {
                exerciseRepository.getExercisesByUuid(
                    listOf(
                        "exercise1",
                        "exercise2",
                    ),
                )
            }
            coVerify(exactly = 1) { exerciseRepository.getExercisesByUuid(listOf("exercise3")) }
            coVerify(exactly = 1) { exerciseRepository.getExercisesByUuid(listOf("exercise4")) }
        }

    @Test
    fun `getChartsData with EXERCISE type returns grouped exercise data with max weights`() =
        runTest(dispatcher) {
            // Given
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
                    sets = listOf(
                        SetsDataModel(Uuid.random().toString(), 10, 50.0, SetsDataType.WORK),
                        SetsDataModel(Uuid.random().toString(), 8, 60.0, SetsDataType.WORK),
                    ),
                    labels = emptyList(),
                    timestamp = 1500L,
                ),
                ExerciseDataModel(
                    uuid = "exercise2",
                    name = "Push ups",
                    trainingUuid = null,
                    sets = listOf(
                        SetsDataModel(Uuid.random().toString(), 12, 65.0, SetsDataType.WORK),
                    ),
                    labels = emptyList(),
                    timestamp = 1800L,
                ),
                ExerciseDataModel(
                    uuid = "exercise3",
                    name = "Bench press",
                    trainingUuid = null,
                    sets = listOf(
                        SetsDataModel(Uuid.random().toString(), 5, 80.0, SetsDataType.WORK),
                    ),
                    labels = emptyList(),
                    timestamp = 1700L,
                ),
            )

            coEvery {
                exerciseRepository.getExercises(
                    name = "Push",
                    startDate = 1000L,
                    endDate = 2000L,
                )
            } returns exerciseData

            // When
            val result = chartsInteractor.getChartsData(params)

            // Then
            assertEquals(2, result.size)

            val pushUpsChart = result.find { it.name == "Push ups" }!!
            assertEquals(2, pushUpsChart.values.size)

            // First exercise: max weight is 60.0
            val firstExercise = pushUpsChart.values.find { it.timestamp == 1500L }!!
            assertEquals(60.0f, firstExercise.value)

            // Second exercise: max weight is 65.0
            val secondExercise = pushUpsChart.values.find { it.timestamp == 1800L }!!
            assertEquals(65.0f, secondExercise.value)

            val benchPressChart = result.find { it.name == "Bench press" }!!
            assertEquals(1, benchPressChart.values.size)
            assertEquals(80.0f, benchPressChart.values[0].value)
            assertEquals(1700L, benchPressChart.values[0].timestamp)

            coVerify(exactly = 1) {
                exerciseRepository.getExercises(
                    name = "Push",
                    startDate = 1000L,
                    endDate = 2000L,
                )
            }
        }

    @Test
    fun `getChartsData with TRAINING type handles empty exercise sets correctly`() =
        runTest(dispatcher) {
            // Given
            val params = ChartParams(
                startDate = 1000L,
                endDate = 2000L,
                name = "Test",
                type = ChartsDomainType.TRAINING,
            )

            val trainingData = listOf(
                TrainingDataModel(
                    uuid = "training1",
                    name = "Test Training",
                    labels = emptyList(),
                    exerciseUuids = listOf("exercise1"),
                    timestamp = 1500L,
                ),
            )

            val exerciseWithEmptySets = ExerciseDataModel(
                uuid = "exercise1",
                name = "Empty Exercise",
                trainingUuid = "training1",
                sets = emptyList(),
                labels = emptyList(),
                timestamp = 1500L,
            )

            coEvery {
                trainingRepository.getTrainings(
                    query = "Test",
                    startDate = 1000L,
                    endDate = 2000L,
                )
            } returns trainingData

            coEvery { exerciseRepository.getExercisesByUuid(listOf("exercise1")) } returns listOf(
                exerciseWithEmptySets,
            )

            // When
            val result = chartsInteractor.getChartsData(params)

            // Then
            assertEquals(1, result.size)
            val chart = result[0]
            assertEquals("Test Training", chart.name)
            assertEquals(1, chart.values.size)
            assertEquals(0.0f, chart.values[0].value) // Should be 0.0 when no sets or no weights
            assertEquals(1500L, chart.values[0].timestamp)
        }

    @Test
    fun `getChartsData with EXERCISE type handles empty sets correctly`() = runTest(dispatcher) {
        // Given
        val params = ChartParams(
            startDate = 1000L,
            endDate = 2000L,
            name = "Test",
            type = ChartsDomainType.EXERCISE,
        )

        val exerciseData = listOf(
            ExerciseDataModel(
                uuid = "exercise1",
                name = "Empty Exercise",
                trainingUuid = null,
                sets = emptyList(),
                labels = emptyList(),
                timestamp = 1500L,
            ),
        )

        coEvery {
            exerciseRepository.getExercises(
                name = "Test",
                startDate = 1000L,
                endDate = 2000L,
            )
        } returns exerciseData

        // When
        val result = chartsInteractor.getChartsData(params)

        // Then
        assertEquals(1, result.size)
        val chart = result[0]
        assertEquals("Empty Exercise", chart.name)
        assertEquals(1, chart.values.size)
        assertEquals(0.0f, chart.values[0].value) // Should be 0.0 when no sets
        assertEquals(1500L, chart.values[0].timestamp)
    }

    @Test
    fun `getChartsData with TRAINING type handles empty training list`() = runTest(dispatcher) {
        // Given
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

        // When
        val result = chartsInteractor.getChartsData(params)

        // Then
        assertTrue(result.isEmpty())
        coVerify(exactly = 1) {
            trainingRepository.getTrainings(
                query = "NonExistent",
                startDate = 1000L,
                endDate = 2000L,
            )
        }
    }

    @Test
    fun `getChartsData with EXERCISE type handles empty exercise list`() = runTest(dispatcher) {
        // Given
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

        // When
        val result = chartsInteractor.getChartsData(params)

        // Then
        assertTrue(result.isEmpty())
        coVerify(exactly = 1) {
            exerciseRepository.getExercises(
                name = "NonExistent",
                startDate = 1000L,
                endDate = 2000L,
            )
        }
    }

    @Test
    fun `getChartsData with TRAINING type handles training with no exercises`() =
        runTest(dispatcher) {
            // Given
            val params = ChartParams(
                startDate = 1000L,
                endDate = 2000L,
                name = "Empty",
                type = ChartsDomainType.TRAINING,
            )

            val trainingData = listOf(
                TrainingDataModel(
                    uuid = "training1",
                    name = "Empty Training",
                    labels = emptyList(),
                    exerciseUuids = listOf("nonexistent1", "nonexistent2"),
                    timestamp = 1500L,
                ),
            )

            coEvery {
                trainingRepository.getTrainings(
                    query = "Empty",
                    startDate = 1000L,
                    endDate = 2000L,
                )
            } returns trainingData

            coEvery {
                exerciseRepository.getExercisesByUuid(
                    listOf(
                        "nonexistent1",
                        "nonexistent2",
                    ),
                )
            } returns emptyList()

            // When
            val result = chartsInteractor.getChartsData(params)

            // Then
            assertEquals(1, result.size)
            val chart = result[0]
            assertEquals("Empty Training", chart.name)
            assertEquals(1, chart.values.size)
            assertEquals(0.0f, chart.values[0].value) // Should be 0.0 when no exercises found
            assertEquals(1500L, chart.values[0].timestamp)
        }

    @Test
    fun `getChartsData with TRAINING type calculates average correctly with mixed weights`() =
        runTest(dispatcher) {
            // Given
            val params = ChartParams(
                startDate = 1000L,
                endDate = 2000L,
                name = "Mixed",
                type = ChartsDomainType.TRAINING,
            )

            val trainingData = listOf(
                TrainingDataModel(
                    uuid = "training1",
                    name = "Mixed Training",
                    labels = emptyList(),
                    exerciseUuids = listOf("exercise1", "exercise2", "exercise3"),
                    timestamp = 1500L,
                ),
            )

            val exercises = listOf(
                // Exercise with max weight 100.0
                ExerciseDataModel(
                    uuid = "exercise1",
                    name = "Exercise 1",
                    trainingUuid = "training1",
                    sets = listOf(
                        SetsDataModel(Uuid.random().toString(), 10, 100.0, SetsDataType.WORK),
                        SetsDataModel(Uuid.random().toString(), 8, 90.0, SetsDataType.WORK),
                    ),
                    labels = emptyList(),
                    timestamp = 1500L,
                ),
                // Exercise with max weight 50.0
                ExerciseDataModel(
                    uuid = "exercise2",
                    name = "Exercise 2",
                    trainingUuid = "training1",
                    sets = listOf(
                        SetsDataModel(Uuid.random().toString(), 12, 50.0, SetsDataType.WORK),
                        SetsDataModel(Uuid.random().toString(), 10, 45.0, SetsDataType.WORK),
                    ),
                    labels = emptyList(),
                    timestamp = 1500L,
                ),
                // Exercise with max weight 75.0
                ExerciseDataModel(
                    uuid = "exercise3",
                    name = "Exercise 3",
                    trainingUuid = "training1",
                    sets = listOf(
                        SetsDataModel(Uuid.random().toString(), 6, 75.0, SetsDataType.WORK),
                    ),
                    labels = emptyList(),
                    timestamp = 1500L,
                ),
            )

            coEvery {
                trainingRepository.getTrainings(
                    query = "Mixed",
                    startDate = 1000L,
                    endDate = 2000L,
                )
            } returns trainingData

            coEvery {
                exerciseRepository.getExercisesByUuid(
                    listOf(
                        "exercise1",
                        "exercise2",
                        "exercise3",
                    ),
                )
            } returns exercises

            // When
            val result = chartsInteractor.getChartsData(params)

            // Then
            assertEquals(1, result.size)
            val chart = result[0]
            assertEquals("Mixed Training", chart.name)
            assertEquals(1, chart.values.size)

            // Average of max weights: (100.0 + 50.0 + 75.0) / 3 = 75.0
            assertEquals(75.0f, chart.values[0].value)
            assertEquals(1500L, chart.values[0].timestamp)
        }

    @Test
    fun `getChartsData preserves correct timestamps for chart items`() = runTest(dispatcher) {
        // Given
        val params = ChartParams(
            startDate = 1000L,
            endDate = 3000L,
            name = "Time",
            type = ChartsDomainType.EXERCISE,
        )

        val exerciseData = listOf(
            ExerciseDataModel(
                uuid = "exercise1",
                name = "Time Exercise",
                trainingUuid = null,
                sets = listOf(SetsDataModel(Uuid.random().toString(), 10, 50.0, SetsDataType.WORK)),
                labels = emptyList(),
                timestamp = 1500L,
            ),
            ExerciseDataModel(
                uuid = "exercise2",
                name = "Time Exercise",
                trainingUuid = null,
                sets = listOf(SetsDataModel(Uuid.random().toString(), 10, 60.0, SetsDataType.WORK)),
                labels = emptyList(),
                timestamp = 2500L,
            ),
        )

        coEvery {
            exerciseRepository.getExercises(
                name = "Time",
                startDate = 1000L,
                endDate = 3000L,
            )
        } returns exerciseData

        // When
        val result = chartsInteractor.getChartsData(params)

        // Then
        assertEquals(1, result.size)
        val chart = result[0]
        assertEquals("Time Exercise", chart.name)
        assertEquals(2, chart.values.size)

        val timestamps = chart.values.map { it.timestamp }.sorted()
        assertEquals(listOf(1500L, 2500L), timestamps)

        val firstItem = chart.values.find { it.timestamp == 1500L }!!
        assertEquals(50.0f, firstItem.value)

        val secondItem = chart.values.find { it.timestamp == 2500L }!!
        assertEquals(60.0f, secondItem.value)
    }

    @Test
    fun `getChartsData with TRAINING type handles single exercise with zero weights`() =
        runTest(dispatcher) {
            // Given
            val params = ChartParams(
                startDate = 1000L,
                endDate = 2000L,
                name = "Zero",
                type = ChartsDomainType.TRAINING,
            )

            val trainingData = listOf(
                TrainingDataModel(
                    uuid = "training1",
                    name = "Zero Training",
                    labels = emptyList(),
                    exerciseUuids = listOf("exercise1"),
                    timestamp = 1500L,
                ),
            )

            val exerciseWithZeroWeights = ExerciseDataModel(
                uuid = "exercise1",
                name = "Zero Exercise",
                trainingUuid = "training1",
                sets = listOf(
                    SetsDataModel(Uuid.random().toString(), 10, 0.0, SetsDataType.WORK),
                    SetsDataModel(Uuid.random().toString(), 8, 0.0, SetsDataType.WORK),
                ),
                labels = emptyList(),
                timestamp = 1500L,
            )

            coEvery {
                trainingRepository.getTrainings(
                    query = "Zero",
                    startDate = 1000L,
                    endDate = 2000L,
                )
            } returns trainingData

            coEvery { exerciseRepository.getExercisesByUuid(listOf("exercise1")) } returns listOf(
                exerciseWithZeroWeights,
            )

            // When
            val result = chartsInteractor.getChartsData(params)

            // Then
            assertEquals(1, result.size)
            val chart = result[0]
            assertEquals("Zero Training", chart.name)
            assertEquals(1, chart.values.size)
            assertEquals(0.0f, chart.values[0].value) // Max of zero weights should be 0.0
            assertEquals(1500L, chart.values[0].timestamp)
        }
}
