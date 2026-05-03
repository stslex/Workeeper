// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.data.exercise.session.model.PerformedExerciseDetailDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionDetailDataModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.past_session.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PastSessionUiMapperTest {

    private val resources = object : ResourceWrapper {
        override fun getString(id: Int, vararg args: Any): String = when (id) {
            R.string.feature_past_session_adhoc_label -> "Ad-hoc workout"
            R.string.feature_past_session_volume_label -> "${args[0]} kg total"
            R.string.feature_past_session_totals_format -> "${args[0]} · ${args[1]}"
            else -> error("Unexpected string id: $id")
        }

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String = when (id) {
            R.plurals.feature_past_session_exercises_count -> {
                if (quantity == 1) "$quantity exercise" else "$quantity exercises"
            }

            R.plurals.feature_past_session_sets_count -> {
                if (quantity == 1) "$quantity set" else "$quantity sets"
            }

            else -> error("Unexpected plural id: $id")
        }

        override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String =
            error("Not used in PastSessionUiMapperTest")

        override fun formatMediumDate(timestamp: Long): String = when (timestamp) {
            90_000L -> "Apr 28"
            else -> error("Unexpected timestamp: $timestamp")
        }
    }

    @Test
    fun `mapper covers adhoc header skipped rows weighted volume and no-set exercise`() {
        val ui = sessionDetail(
            isAdhoc = true,
            exercises = listOf(
                weightlessExercise(position = 1),
                weightedExercise(position = 2),
                skippedExercise(position = 3),
            ),
        ).toUi(resources)

        assertEquals("Ad-hoc workout", ui.trainingName)
        assertEquals("Apr 28", ui.finishedAtAbsoluteLabel)
        assertEquals("01:30", ui.durationLabel)
        assertEquals("2 exercises · 3 sets", ui.totalsLabel)
        assertEquals("770 kg total", ui.volumeLabel)

        assertEquals(listOf("Pull Up", "Bench", "Skipped Fly"), ui.exercises.map { it.exerciseName })
        assertEquals(false, ui.exercises[0].isWeighted)
        assertEquals(true, ui.exercises[1].isWeighted)
        assertEquals(true, ui.exercises[2].skipped)
        assertTrue(ui.exercises[2].sets.isEmpty())
        assertEquals(listOf(0, 1), ui.exercises[1].sets.map { it.position })
        assertEquals(listOf(SetTypeUiModel.WORK, SetTypeUiModel.FAILURE), ui.exercises[1].sets.map { it.type })
    }

    @Test
    fun `mapper marks the PR-bearing set with isPersonalRecord`() {
        val ui = sessionDetail(
            isAdhoc = false,
            exercises = listOf(weightedExercise(position = 0)),
        ).toUi(resources, prSetUuids = setOf("set-2"))

        val sets = ui.exercises.single().sets
        assertEquals(true, sets.first { it.setUuid == "set-2" }.isPersonalRecord)
        assertFalse(sets.first { it.setUuid == "set-3" }.isPersonalRecord)
    }

    @Test
    fun `mapper leaves all sets unflagged when prSetUuids is empty`() {
        val ui = sessionDetail(
            isAdhoc = false,
            exercises = listOf(weightedExercise(position = 0)),
        ).toUi(resources, prSetUuids = emptySet())

        ui.exercises.single().sets.forEach { assertFalse(it.isPersonalRecord) }
    }

    @Test
    fun `mapper leaves volume label null when no weighted work or fail sets qualify`() {
        val ui = sessionDetail(
            isAdhoc = false,
            exercises = listOf(
                PerformedExerciseDetailDataModel(
                    performedExerciseUuid = "performed-3",
                    exerciseUuid = "exercise-3",
                    exerciseName = "Bench",
                    exerciseType = ExerciseTypeDataModel.WEIGHTED,
                    position = 0,
                    skipped = false,
                    sets = listOf(
                        SetsDataModel(
                            uuid = "set-4",
                            reps = 5,
                            weight = 100.0,
                            type = SetsDataType.WARM,
                        ),
                        SetsDataModel(
                            uuid = "set-5",
                            reps = 10,
                            weight = 40.0,
                            type = SetsDataType.DROP,
                        ),
                    ),
                ),
            ),
        ).toUi(resources)

        assertEquals("Push Day", ui.trainingName)
        assertNull(ui.volumeLabel)
    }

    private fun sessionDetail(
        isAdhoc: Boolean,
        exercises: List<PerformedExerciseDetailDataModel>,
    ) = SessionDetailDataModel(
        sessionUuid = "session-1",
        trainingUuid = "training-1",
        trainingName = "Push Day",
        isAdhoc = isAdhoc,
        startedAt = 0L,
        finishedAt = 90_000L,
        exercises = exercises,
    )

    private fun weightlessExercise(position: Int) = PerformedExerciseDetailDataModel(
        performedExerciseUuid = "performed-1",
        exerciseUuid = "exercise-1",
        exerciseName = "Pull Up",
        exerciseType = ExerciseTypeDataModel.WEIGHTLESS,
        position = position,
        skipped = false,
        sets = listOf(
            SetsDataModel(
                uuid = "set-1",
                reps = 10,
                weight = null,
                type = SetsDataType.WORK,
            ),
        ),
    )

    private fun weightedExercise(position: Int) = PerformedExerciseDetailDataModel(
        performedExerciseUuid = "performed-2",
        exerciseUuid = "exercise-2",
        exerciseName = "Bench",
        exerciseType = ExerciseTypeDataModel.WEIGHTED,
        position = position,
        skipped = false,
        sets = listOf(
            SetsDataModel(
                uuid = "set-2",
                reps = 5,
                weight = 100.0,
                type = SetsDataType.WORK,
            ),
            SetsDataModel(
                uuid = "set-3",
                reps = 3,
                weight = 90.0,
                type = SetsDataType.FAIL,
            ),
        ),
    )

    private fun skippedExercise(position: Int) = PerformedExerciseDetailDataModel(
        performedExerciseUuid = "performed-4",
        exerciseUuid = "exercise-4",
        exerciseName = "Skipped Fly",
        exerciseType = ExerciseTypeDataModel.WEIGHTED,
        position = position,
        skipped = true,
        sets = emptyList(),
    )
}
