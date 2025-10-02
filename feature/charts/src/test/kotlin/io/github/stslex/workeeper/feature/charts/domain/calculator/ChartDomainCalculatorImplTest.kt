package io.github.stslex.workeeper.feature.charts.domain.calculator

import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.feature.charts.domain.model.ChartDataType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertNotNull
import kotlin.uuid.Uuid

class ChartDomainCalculatorImplTest {

    private val calculator = ChartDomainCalculatorImpl()

    @Test
    fun `mapTrainings groups trainings by name and calculates average max weights`() = runTest {
        // Given
        val trainings = listOf(
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

        val getExercises: suspend (List<String>) -> List<ExerciseDataModel> = { uuids ->
            when {
                uuids.contains("exercise1") && uuids.contains("exercise2") -> listOf(
                    ExerciseDataModel(
                        uuid = "exercise1",
                        name = "Push ups",
                        trainingUuid = "training1",
                        sets = listOf(
                            SetsDataModel(Uuid.random().toString(), 10, 50.0, SetsDataType.WORK),
                            SetsDataModel(Uuid.random().toString(), 8, 60.0, SetsDataType.WORK),
                        ),
                        labels = emptyList(),
                        timestamp = 1500L,
                    ),
                    ExerciseDataModel(
                        uuid = "exercise2",
                        name = "Bench press",
                        trainingUuid = "training1",
                        sets = listOf(
                            SetsDataModel(Uuid.random().toString(), 5, 80.0, SetsDataType.WORK),
                        ),
                        labels = emptyList(),
                        timestamp = 1500L,
                    ),
                )

                uuids.contains("exercise3") -> listOf(
                    ExerciseDataModel(
                        uuid = "exercise3",
                        name = "Dips",
                        trainingUuid = "training2",
                        sets = listOf(
                            SetsDataModel(Uuid.random().toString(), 12, 40.0, SetsDataType.WORK),
                        ),
                        labels = emptyList(),
                        timestamp = 1800L,
                    ),
                )

                uuids.contains("exercise4") -> listOf(
                    ExerciseDataModel(
                        uuid = "exercise4",
                        name = "Pull ups",
                        trainingUuid = "training3",
                        sets = listOf(
                            SetsDataModel(Uuid.random().toString(), 8, 70.0, SetsDataType.WORK),
                        ),
                        labels = emptyList(),
                        timestamp = 1700L,
                    ),
                )

                else -> emptyList()
            }
        }

        // When
        val result = calculator.mapTrainings(
            startTimestamp = 1000L,
            endTimestamp = 2000L,
            trainings = trainings,
            getExercises = getExercises,
        )

        // Then
        assertEquals(2, result.size)

        val pushDayChart = result.find { it.name == "Push Day" }
        assertNotNull(pushDayChart)
        assertEquals(ChartDataType.DAY, pushDayChart.dateType)

        val pullDayChart = result.find { it.name == "Pull Day" }
        assertNotNull(pullDayChart)
        assertEquals(ChartDataType.DAY, pullDayChart.dateType)
    }

    @Test
    fun `mapExercises groups exercises by name and calculates max weights`() = runTest {
        // Given
        val exercises = listOf(
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

        // When
        val result = calculator.mapExercises(
            startTimestamp = 1000L,
            endTimestamp = 2000L,
            exercises = exercises,
        )

        // Then
        assertEquals(2, result.size)

        val pushUpsChart = result.find { it.name == "Push ups" }
        assertNotNull(pushUpsChart)
        assertEquals(ChartDataType.DAY, pushUpsChart.dateType)

        val benchPressChart = result.find { it.name == "Bench press" }
        assertNotNull(benchPressChart)
        assertEquals(ChartDataType.DAY, benchPressChart.dateType)
    }

    @Test
    fun `mapTrainings handles empty exercise lists correctly`() = runTest {
        // Given
        val trainings = listOf(
            TrainingDataModel(
                uuid = "training1",
                name = "Empty Training",
                labels = emptyList(),
                exerciseUuids = listOf("exercise1"),
                timestamp = 1500L,
            ),
        )

        val getExercises: suspend (List<String>) -> List<ExerciseDataModel> = { emptyList() }

        // When
        val result = calculator.mapTrainings(
            startTimestamp = 1000L,
            endTimestamp = 2000L,
            trainings = trainings,
            getExercises = getExercises,
        )

        // Then
        assertEquals(1, result.size)
        assertEquals("Empty Training", result[0].name)
        assertEquals(ChartDataType.DAY, result[0].dateType)
    }

    @Test
    fun `mapExercises handles empty sets correctly`() = runTest {
        // Given
        val exercises = listOf(
            ExerciseDataModel(
                uuid = "exercise1",
                name = "Empty Exercise",
                trainingUuid = null,
                sets = emptyList(),
                labels = emptyList(),
                timestamp = 1500L,
            ),
        )

        // When
        val result = calculator.mapExercises(
            startTimestamp = 1000L,
            endTimestamp = 2000L,
            exercises = exercises,
        )

        // Then
        assertEquals(1, result.size)
        assertEquals("Empty Exercise", result[0].name)
        assertEquals(ChartDataType.DAY, result[0].dateType)
    }

    @Test
    fun `init method sets calculate params correctly`() {
        // Given
        val startTimestamp = 1000L
        val endTimestamp = 2000L

        assertDoesNotThrow { calculator.init(startTimestamp, endTimestamp) }
    }

    @Test
    fun `different time ranges produce different chart data types`() = runTest {
        // Given - Exercise data with timestamp that falls within our test ranges
        val exercises = listOf(
            ExerciseDataModel(
                uuid = "exercise1",
                name = "Test Exercise",
                trainingUuid = null,
                sets = listOf(
                    SetsDataModel(Uuid.random().toString(), 10, 50.0, SetsDataType.WORK),
                ),
                labels = emptyList(),
                timestamp = 86401000L, // timestamp within our test range
            ),
        )

        // Test different time ranges with fresh calculator instances
        val dayCalculator = ChartDomainCalculatorImpl()
        val weekCalculator = ChartDomainCalculatorImpl()

        // Test short range (should be DAY) - 1 day = 86400000ms
        val dayResult = dayCalculator.mapExercises(
            startTimestamp = 86400000L,
            endTimestamp = 86400000L + 86400000L, // 1 day duration
            exercises = exercises,
        )

        // Test longer range (should be WEEK) - 28 days
        val weekResult = weekCalculator.mapExercises(
            startTimestamp = 86400000L,
            endTimestamp = 86400000L + (86400000L * 28), // 28 days duration
            exercises = exercises,
        )

        // Then
        assertTrue(dayResult.isNotEmpty(), "Day result should not be empty")
        assertTrue(weekResult.isNotEmpty(), "Week result should not be empty")
        assertEquals(ChartDataType.DAY, dayResult[0].dateType)
        assertEquals(ChartDataType.WEEK, weekResult[0].dateType)
    }

    @Test
    fun `mapExercises calculates xValue and yValue correctly for DAY type`() = runTest {
        // Given - Multiple exercises with known weights and timestamps
        val startTimestamp = 0L
        val oneDayMs = 86400000L
        val endTimestamp = oneDayMs // 1 day duration

        val exercises = listOf(
            ExerciseDataModel(
                uuid = "exercise1",
                name = "Bench Press",
                trainingUuid = null,
                sets = listOf(
                    SetsDataModel(Uuid.random().toString(), 10, 100.0, SetsDataType.WORK),
                    SetsDataModel(Uuid.random().toString(), 8, 120.0, SetsDataType.WORK),
                ),
                labels = emptyList(),
                timestamp = oneDayMs / 2, // Middle of the day
            ),
        )

        // When
        val result = calculator.mapExercises(
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            exercises = exercises,
        )

        // Then
        assertEquals(1, result.size)
        val chart = result[0]
        assertEquals("Bench Press", chart.name)
        assertEquals(ChartDataType.DAY, chart.dateType)
        assertEquals(1, chart.values.size) // 1 day = 1 data point

        val dataPoint = chart.values[0]
        assertEquals(0.0f, dataPoint.xValue) // Single point should be at position 0
        assertEquals(120.0f, dataPoint.yValue) // Max weight from sets
    }

    @Test
    fun `mapExercises calculates xValue and yValue correctly for WEEK type`() = runTest {
        // Given - Exercise data across multiple weeks
        val startTimestamp = 0L
        val oneDayMs = 86400000L
        val oneWeekMs = oneDayMs * 7
        val endTimestamp = oneWeekMs * 4 // 4 weeks duration

        val exercises = listOf(
            ExerciseDataModel(
                uuid = "exercise1",
                name = "Squat",
                trainingUuid = null,
                sets = listOf(
                    SetsDataModel(Uuid.random().toString(), 10, 80.0, SetsDataType.WORK),
                ),
                labels = emptyList(),
                timestamp = oneWeekMs, // Start of week 2
            ),
            ExerciseDataModel(
                uuid = "exercise2",
                name = "Squat",
                trainingUuid = null,
                sets = listOf(
                    SetsDataModel(Uuid.random().toString(), 8, 90.0, SetsDataType.WORK),
                ),
                labels = emptyList(),
                timestamp = oneWeekMs * 3, // Start of week 4
            ),
        )

        // When
        val result = calculator.mapExercises(
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            exercises = exercises,
        )

        // Then
        assertEquals(1, result.size)
        val chart = result[0]
        assertEquals("Squat", chart.name)
        assertEquals(ChartDataType.WEEK, chart.dateType)
        assertEquals(4, chart.values.size) // 4 weeks = 4 data points

        // Verify xValue progression (should be 0, 1/3, 2/3, 1.0 for 4 weeks with new calculation)
        assertEquals(0.0f, chart.values[0].xValue)
        assertEquals(0.33333334f, chart.values[1].xValue, 0.0001f)
        assertEquals(0.6666667f, chart.values[2].xValue, 0.0001f)
        assertEquals(1.0f, chart.values[3].xValue, 0.0001f)

        // Verify yValues - should have data for weeks 2 and 4, null for weeks 1 and 3
        assertEquals(null, chart.values[0].yValue) // Week 1 - no data
        assertEquals(80.0f, chart.values[1].yValue) // Week 2 - exercise1
        assertEquals(null, chart.values[2].yValue) // Week 3 - no data
        assertEquals(90.0f, chart.values[3].yValue) // Week 4 - exercise2
    }

    @Test
    fun `mapTrainings calculates average max weights correctly`() = runTest {
        // Given
        val startTimestamp = 0L
        val endTimestamp = 86400000L // 1 day

        val trainings = listOf(
            TrainingDataModel(
                uuid = "training1",
                name = "Push Day",
                labels = emptyList(),
                exerciseUuids = listOf("exercise1", "exercise2"),
                timestamp = 43200000L, // Middle of the day
            ),
        )

        val getExercises: suspend (List<String>) -> List<ExerciseDataModel> = { uuids ->
            listOf(
                ExerciseDataModel(
                    uuid = "exercise1",
                    name = "Bench Press",
                    trainingUuid = "training1",
                    sets = listOf(
                        SetsDataModel(Uuid.random().toString(), 10, 100.0, SetsDataType.WORK),
                        SetsDataModel(Uuid.random().toString(), 8, 120.0, SetsDataType.WORK),
                    ),
                    labels = emptyList(),
                    timestamp = 43200000L,
                ),
                ExerciseDataModel(
                    uuid = "exercise2",
                    name = "Overhead Press",
                    trainingUuid = "training1",
                    sets = listOf(
                        SetsDataModel(Uuid.random().toString(), 10, 60.0, SetsDataType.WORK),
                        SetsDataModel(Uuid.random().toString(), 8, 80.0, SetsDataType.WORK),
                    ),
                    labels = emptyList(),
                    timestamp = 43200000L,
                ),
            )
        }

        // When
        val result = calculator.mapTrainings(
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            trainings = trainings,
            getExercises = getExercises,
        )

        // Then
        assertEquals(1, result.size)
        val chart = result[0]
        assertEquals("Push Day", chart.name)
        assertEquals(1, chart.values.size)

        val dataPoint = chart.values[0]
        assertEquals(0.0f, dataPoint.xValue) // Single point should be at position 0
        // Average of max weights: (120.0 + 80.0) / 2 = 100.0
        assertEquals(100.0f, dataPoint.yValue)
    }

    @Test
    fun `mapExercises handles timestamp positioning correctly`() = runTest {
        // Given - Exercise positioned at specific time within range
        val startTimestamp = 1000000L
        val oneDayMs = 86400000L
        val endTimestamp = startTimestamp + oneDayMs * 7 // 7 days = 1 week duration

        val exercises = listOf(
            ExerciseDataModel(
                uuid = "exercise1",
                name = "Deadlift",
                trainingUuid = null,
                sets = listOf(
                    SetsDataModel(Uuid.random().toString(), 5, 200.0, SetsDataType.WORK),
                ),
                labels = emptyList(),
                timestamp = startTimestamp + oneDayMs * 3, // Day 4 of the week
            ),
        )

        // When
        val result = calculator.mapExercises(
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            exercises = exercises,
        )

        // Then
        assertEquals(1, result.size)
        val chart = result[0]
        assertEquals(ChartDataType.DAY, chart.dateType) // 7 days ≤ 14, so DAY type
        assertEquals(7, chart.values.size) // 7 days

        // The exercise should appear in the correct day position
        // Days 1-3 should be null, day 4 should have the value, days 5-7 should be null
        assertEquals(null, chart.values[0].yValue) // Day 1
        assertEquals(null, chart.values[1].yValue) // Day 2
        assertEquals(null, chart.values[2].yValue) // Day 3
        assertEquals(200.0f, chart.values[3].yValue) // Day 4 - exercise timestamp
        assertEquals(null, chart.values[4].yValue) // Day 5
        assertEquals(null, chart.values[5].yValue) // Day 6
        assertEquals(null, chart.values[6].yValue) // Day 7
    }

    @Test
    fun `mapExercises handles edge cases and boundary conditions`() = runTest {
        // Given - Edge case scenarios
        val startTimestamp = 1000000L
        val oneDayMs = 86400000L
        val endTimestamp = startTimestamp + oneDayMs * 5 // 5 days

        val exercises = listOf(
            // Exercise exactly at start boundary
            ExerciseDataModel(
                uuid = "exercise1",
                name = "Morning Exercise",
                trainingUuid = null,
                sets = listOf(
                    SetsDataModel(Uuid.random().toString(), 10, 50.0, SetsDataType.WORK),
                ),
                labels = emptyList(),
                timestamp = startTimestamp,
            ),
            // Exercise near end but within the last day's time window
            ExerciseDataModel(
                uuid = "exercise2",
                name = "Evening Exercise",
                trainingUuid = null,
                sets = listOf(
                    SetsDataModel(Uuid.random().toString(), 8, 75.0, SetsDataType.WORK),
                ),
                labels = emptyList(),
                timestamp = startTimestamp + oneDayMs * 4 + (oneDayMs / 2), // Middle of day 5
            ),
            // Exercise with no sets (should return 0)
            ExerciseDataModel(
                uuid = "exercise3",
                name = "No Sets Exercise",
                trainingUuid = null,
                sets = emptyList(),
                labels = emptyList(),
                timestamp = startTimestamp + oneDayMs * 2,
            ),
        )

        // When
        val result = calculator.mapExercises(
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            exercises = exercises,
        )

        // Then
        assertEquals(3, result.size)

        // Verify Morning Exercise (at start boundary)
        val morningChart = result.find { it.name == "Morning Exercise" }!!
        assertEquals(5, morningChart.values.size)
        assertEquals(50.0f, morningChart.values[0].yValue) // Should be in first day
        assertEquals(null, morningChart.values[1].yValue)

        // Verify Evening Exercise (in day 5)
        val eveningChart = result.find { it.name == "Evening Exercise" }!!
        assertEquals(75.0f, eveningChart.values[4].yValue) // Should be in day 5

        // Verify No Sets Exercise (empty sets)
        val noSetsChart = result.find { it.name == "No Sets Exercise" }!!
        assertEquals(0.0f, noSetsChart.values[2].yValue) // Should be 0 for empty sets
    }

    @Test
    fun `mapExercises calculates xValue progression correctly for MONTH type`() = runTest {
        // Given - Long time range to trigger MONTH chart type
        val startTimestamp = 0L
        val oneDayMs = 86400000L
        val endTimestamp = oneDayMs * 90 // 90 days = 3 months

        val exercises = listOf(
            ExerciseDataModel(
                uuid = "exercise1",
                name = "Monthly Exercise",
                trainingUuid = null,
                sets = listOf(
                    SetsDataModel(Uuid.random().toString(), 10, 100.0, SetsDataType.WORK),
                ),
                labels = emptyList(),
                timestamp = oneDayMs * 30, // Middle of range
            ),
        )

        // When
        val result = calculator.mapExercises(
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            exercises = exercises,
        )

        // Then
        assertEquals(1, result.size)
        val chart = result[0]
        assertEquals(ChartDataType.MONTH, chart.dateType) // 90 days > 60, so MONTH type
        assertEquals(3, chart.values.size) // 90 days / 30 = 3 months

        // Verify xValue progression for months (3 months: 0, 0.5, 1.0 with new calculation)
        assertEquals(0.0f, chart.values[0].xValue) // Month 1: 0
        assertEquals(0.5f, chart.values[1].xValue, 0.0001f) // Month 2: 1 / (3-1) = 0.5
        assertEquals(1.0f, chart.values[2].xValue, 0.0001f) // Month 3: 2 / (3-1) = 1.0

        // Exercise should appear in month 2 (day 30 falls in second month)
        assertEquals(null, chart.values[0].yValue) // Month 1
        assertEquals(100.0f, chart.values[1].yValue) // Month 2 - exercise data
        assertEquals(null, chart.values[2].yValue) // Month 3
    }

    @Test
    fun `mapTrainings handles multiple exercises with different weights per training`() = runTest {
        // Given - Training with exercises having varied weights
        val startTimestamp = 0L
        val endTimestamp = 86400000L // 1 day

        val trainings = listOf(
            TrainingDataModel(
                uuid = "training1",
                name = "Full Body Workout",
                labels = emptyList(),
                exerciseUuids = listOf("exercise1", "exercise2", "exercise3"),
                timestamp = 43200000L, // Middle of day
            ),
        )

        val getExercises: suspend (List<String>) -> List<ExerciseDataModel> = { uuids ->
            listOf(
                // Light weight exercise
                ExerciseDataModel(
                    uuid = "exercise1",
                    name = "Warm-up",
                    trainingUuid = "training1",
                    sets = listOf(
                        SetsDataModel(Uuid.random().toString(), 15, 20.0, SetsDataType.WORK),
                        SetsDataModel(Uuid.random().toString(), 12, 25.0, SetsDataType.WORK),
                    ),
                    labels = emptyList(),
                    timestamp = 43200000L,
                ),
                // Heavy weight exercise
                ExerciseDataModel(
                    uuid = "exercise2",
                    name = "Main Lift",
                    trainingUuid = "training1",
                    sets = listOf(
                        SetsDataModel(Uuid.random().toString(), 5, 150.0, SetsDataType.WORK),
                        SetsDataModel(Uuid.random().toString(), 3, 200.0, SetsDataType.WORK),
                    ),
                    labels = emptyList(),
                    timestamp = 43200000L,
                ),
                // Medium weight exercise
                ExerciseDataModel(
                    uuid = "exercise3",
                    name = "Accessory",
                    trainingUuid = "training1",
                    sets = listOf(
                        SetsDataModel(Uuid.random().toString(), 10, 80.0, SetsDataType.WORK),
                        SetsDataModel(Uuid.random().toString(), 8, 90.0, SetsDataType.WORK),
                    ),
                    labels = emptyList(),
                    timestamp = 43200000L,
                ),
            )
        }

        // When
        val result = calculator.mapTrainings(
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            trainings = trainings,
            getExercises = getExercises,
        )

        // Then
        assertEquals(1, result.size)
        val chart = result[0]
        assertEquals("Full Body Workout", chart.name)
        assertEquals(1, chart.values.size)

        val dataPoint = chart.values[0]
        assertEquals(0.0f, dataPoint.xValue) // Single point should be at position 0

        // Average of max weights: (25.0 + 200.0 + 90.0) / 3 = 105.0
        assertEquals(105.0f, dataPoint.yValue)
    }

    @Test
    fun `ChartDomainCalculateParams calculates values correctly for different scenarios`() {
        // Test direct instantiation of now-public ChartDomainCalculateParams
        val calculator = ChartDomainCalculatorImpl()

        // Test single point scenario (xValues = 1)
        calculator.init(1000L, 2000L) // 1 day

        // Test zero values scenario
        val zeroParams = ChartDomainCalculatorImpl.ChartDomainCalculateParams(
            start = 1000L,
            end = 1000L,
            xValues = 0,
            type = ChartDataType.DAY,
        )

        assertEquals(0L, zeroParams.diff)
        assertEquals(0f, zeroParams.itemDiffK)

        // Test normal scenario
        val testParams = ChartDomainCalculatorImpl.ChartDomainCalculateParams(
            start = 0L,
            end = 86400000L,
            xValues = 5,
            type = ChartDataType.DAY,
        )

        assertEquals(86400000L, testParams.diff)
        assertEquals(0.25f, testParams.itemDiffK) // 1 / (5-1) = 0.25
    }

    @Test
    fun `ChartDomainCalculateParams handles edge cases properly`() {
        val calculator = ChartDomainCalculatorImpl()

        // Test very small time differences
        assertDoesNotThrow {
            calculator.init(1000L, 1001L) // 1 millisecond difference
        }

        // Test very large time differences (multiple years)
        assertDoesNotThrow {
            calculator.init(0L, 86400000L * 365 * 2) // 2 years
        }

        // Test zero time difference
        assertDoesNotThrow {
            calculator.init(1000L, 1000L) // Same start and end
        }
    }

    @Test
    fun `ChartDomainCalculateParams data class properties work correctly`() {
        // Test the now-public data class directly
        val params = ChartDomainCalculatorImpl.ChartDomainCalculateParams(
            start = 1000L,
            end = 2000L,
            xValues = 5,
            type = ChartDataType.WEEK,
        )

        // Verify primary constructor parameters
        assertEquals(1000L, params.start)
        assertEquals(2000L, params.end)
        assertEquals(5, params.xValues)
        assertEquals(ChartDataType.WEEK, params.type)

        // Verify computed properties
        assertEquals(1000L, params.diff) // end - start
        assertEquals(200.0f, params.diffSingle) // diff / xValues = 1000 / 5
        assertEquals(100.0f, params.halfDiffSingle) // diffSingle / 2
        assertEquals(0.25f, params.itemDiffK) // 1 / (xValues - 1) = 1 / 4

        // Test edge cases
        val singlePointParams = ChartDomainCalculatorImpl.ChartDomainCalculateParams(
            start = 0L,
            end = 1000L,
            xValues = 1,
            type = ChartDataType.DAY,
        )

        assertEquals(0f, singlePointParams.itemDiffK) // Should be 0 for single point

        val zeroXValuesParams = ChartDomainCalculatorImpl.ChartDomainCalculateParams(
            start = 0L,
            end = 1000L,
            xValues = 0,
            type = ChartDataType.DAY,
        )

        assertEquals(0f, zeroXValuesParams.diffSingle) // Should be 0 for zero xValues
        assertEquals(0f, zeroXValuesParams.itemDiffK) // Should be 0 for zero xValues
    }

    @Test
    fun `calculator handles parameter caching correctly`() {
        val calculator = ChartDomainCalculatorImpl()

        // First call should create parameters
        assertDoesNotThrow {
            calculator.init(1000L, 5000L)
        }

        // Second call with same parameters should reuse cached params
        assertDoesNotThrow {
            calculator.init(1000L, 5000L)
        }

        // Call with different parameters should create new params
        assertDoesNotThrow {
            calculator.init(2000L, 6000L)
        }
    }

    @Test
    fun `ChartDomainCalculateParams handles various time ranges and xValue calculations`() =
        runTest {
            val calculator = ChartDomainCalculatorImpl()

            // Test 1: Single day (xValues = 1, should use special case)
            val singleDayExercises = listOf(
                ExerciseDataModel(
                    uuid = "exercise1",
                    name = "Test",
                    trainingUuid = null,
                    sets = listOf(
                        SetsDataModel(
                            Uuid.random().toString(),
                            10,
                            50.0,
                            SetsDataType.WORK,
                        ),
                    ),
                    labels = emptyList(),
                    timestamp = 43200000L, // Middle of range
                ),
            )

            val singleDayResult = calculator.mapExercises(
                startTimestamp = 0L,
                endTimestamp = 86400000L, // 1 day
                exercises = singleDayExercises,
            )

            assertEquals(1, singleDayResult[0].values.size)
            assertEquals(0.0f, singleDayResult[0].values[0].xValue) // Single point at 0

            // Test 2: Two days (xValues = 2, should calculate 1/(2-1) = 1.0 progression)
            val twoDayResult = calculator.mapExercises(
                startTimestamp = 0L,
                endTimestamp = 86400000L * 2, // 2 days
                exercises = singleDayExercises,
            )

            assertEquals(2, twoDayResult[0].values.size)
            assertEquals(0.0f, twoDayResult[0].values[0].xValue) // First point
            assertEquals(1.0f, twoDayResult[0].values[1].xValue) // Second point

            // Test 3: Five days (xValues = 5, should calculate 1/(5-1) = 0.25 increments)
            val fiveDayResult = calculator.mapExercises(
                startTimestamp = 0L,
                endTimestamp = 86400000L * 5, // 5 days
                exercises = singleDayExercises,
            )

            assertEquals(5, fiveDayResult[0].values.size)
            assertEquals(0.0f, fiveDayResult[0].values[0].xValue)
            assertEquals(0.25f, fiveDayResult[0].values[1].xValue)
            assertEquals(0.5f, fiveDayResult[0].values[2].xValue)
            assertEquals(0.75f, fiveDayResult[0].values[3].xValue)
            assertEquals(1.0f, fiveDayResult[0].values[4].xValue)
        }
}
