// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.past_session.R
import io.github.stslex.workeeper.feature.past_session.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.PerformedExerciseDetailDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.SessionDetailDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.SetDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.SetTypeDomain
import io.github.stslex.workeeper.feature.past_session.mvi.mapper.PastSessionUiMapper.toUi
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastExerciseUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSessionUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PastSessionUiMapperTest {

    private val resources = object : ResourceWrapper {
        override fun getString(id: Int, vararg args: Any): String = when (id) {
            R.string.feature_past_session_adhoc_label -> "Ad-hoc workout"
            R.string.feature_past_session_totals_format -> "${args[0]} · ${args[1]}"
            R.string.feature_past_session_totals_format_with_tonnage ->
                "${args[0]} · ${args[1]} · ${args[2]}"
            // The real resource is "%,d kg"; the grouping comes from `Resources.getString`
            // against the configuration locale, which is not exercised on the host JVM.
            R.string.feature_past_session_tonnage_format -> "${args[0]} kg"
            else -> error("Unexpected string id: $id")
        }

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String =
            when (id) {
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
        // Tonnage counts the weighted exercise only: 5x100 + 3x90 = 770. The weightless
        // Pull Up's 10 reps contribute nothing, and the skipped Fly has no sets.
        assertEquals("2 exercises · 3 sets · 770 kg", ui.totalsLabel)

        assertEquals(
            listOf("Pull Up", "Bench", "Skipped Fly"),
            ui.exercises.map { it.exerciseName },
        )
        assertEquals(false, ui.exercises[0].isWeighted)
        assertEquals(true, ui.exercises[1].isWeighted)
        assertEquals(true, ui.exercises[2].skipped)
        assertTrue(ui.exercises[2].sets.isEmpty())
        assertEquals(listOf(0, 1), ui.exercises[1].sets.map { it.position })
        assertEquals(
            listOf(SetTypeUiModel.WORK, SetTypeUiModel.FAILURE),
            ui.exercises[1].sets.map { it.type },
        )
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
    fun `mapper sums tonnage over weighted sets`() {
        // Replaces the v2.4 5.7 guard `mapper does not surface a volume label even when
        // weighted sets are present`, which existed to keep total-kg OUT of the header.
        // Spec §11.1 reverses that decision, so the guard is retired rather than broken.
        val ui = sessionDetail(
            isAdhoc = false,
            exercises = listOf(
                PerformedExerciseDetailDomain(
                    performedExerciseUuid = "performed-3",
                    exerciseUuid = "exercise-3",
                    exerciseName = "Bench",
                    exerciseType = ExerciseTypeDomain.WEIGHTED,
                    position = 0,
                    skipped = false,
                    sets = listOf(
                        SetDomain(
                            uuid = "set-4",
                            reps = 5,
                            weight = 100.0,
                            type = SetTypeDomain.WORK,
                            position = 0,
                        ),
                    ),
                ),
            ),
        ).toUi(resources)

        assertEquals("Push Day", ui.trainingName)
        assertEquals("1 exercise · 1 set · 500 kg", ui.totalsLabel)
    }

    @Test
    fun `mapper excludes a weightless exercise carrying a residual weight from tonnage`() {
        // The reason the predicate is `type == WEIGHTED` and not `weight ?: 0.0`. `SetEntity`
        // does not constrain weight by exercise type, residual non-null weights on weightless
        // rows exist in shipped data, and spec §12 rejected scrubbing them by migration. A sum
        // that read the column regardless would report 300 kg of work that was never lifted.
        val ui = sessionDetail(
            isAdhoc = false,
            exercises = listOf(
                PerformedExerciseDetailDomain(
                    performedExerciseUuid = "performed-5",
                    exerciseUuid = "exercise-5",
                    exerciseName = "Pull Up",
                    exerciseType = ExerciseTypeDomain.WEIGHTLESS,
                    position = 0,
                    skipped = false,
                    sets = listOf(
                        SetDomain(
                            uuid = "set-5",
                            reps = 10,
                            weight = 30.0,
                            type = SetTypeDomain.WORK,
                            position = 0,
                        ),
                    ),
                ),
            ),
        ).toUi(resources)

        // No tonnage term at all — not "· 0 kg".
        assertEquals("1 exercise · 1 set", ui.totalsLabel)
    }

    @Test
    fun `mapper omits the tonnage term when a weighted session logged no weights`() {
        val ui = sessionDetail(
            isAdhoc = false,
            exercises = listOf(
                PerformedExerciseDetailDomain(
                    performedExerciseUuid = "performed-6",
                    exerciseUuid = "exercise-6",
                    exerciseName = "Bench",
                    exerciseType = ExerciseTypeDomain.WEIGHTED,
                    position = 0,
                    skipped = false,
                    sets = listOf(
                        SetDomain(
                            uuid = "set-6",
                            reps = 8,
                            weight = null,
                            type = SetTypeDomain.WORK,
                            position = 0,
                        ),
                    ),
                ),
            ),
        ).toUi(resources)

        assertEquals("1 exercise · 1 set", ui.totalsLabel)
    }

    // --- setSummary: the collapsed card's plan line --------------------------------------

    @Test
    fun `mapper builds the collapsed summary per exercise type`() {
        // The whole point of these assertions: `setSummary` is read nowhere else in the
        // suite. The goldens hand-write the string into their fixtures and never call
        // `toUi`, so before this test `return ""` kept everything green while collapsed
        // cards silently lost their plan line.
        val ui = sessionDetail(
            isAdhoc = false,
            exercises = listOf(
                weightlessExercise(position = 1),
                weightedExercise(position = 2),
                skippedExercise(position = 3),
            ),
        ).toUi(resources)

        // Weighted: `{weight}×{reps}` per set, joined by " · " (U+00B7 with hair spaces).
        assertEquals("100×5 · 90×3", ui.exercises[1].setSummary)
        // Weightless: bare rep counts — the weight dimension does not exist for the type.
        assertEquals("10", ui.exercises[0].setSummary)
        // No sets logged: empty, which is what lets the card omit the line entirely.
        assertEquals("", ui.exercises[2].setSummary)
        // The separator is U+00D7, not the Latin letter x.
        assertTrue(ui.exercises[1].setSummary.contains('×'))
    }

    @Test
    fun `mapper excludes unfilled sets from the collapsed summary`() {
        // `reps > 0` is the same sentinel the set count uses, so a card cannot summarise a
        // row the header refuses to count.
        val ui = sessionDetail(
            isAdhoc = false,
            exercises = listOf(
                PerformedExerciseDetailDomain(
                    performedExerciseUuid = "performed-7",
                    exerciseUuid = "exercise-7",
                    exerciseName = "Bench",
                    exerciseType = ExerciseTypeDomain.WEIGHTED,
                    position = 0,
                    skipped = false,
                    sets = listOf(
                        SetDomain("set-7a", reps = 5, weight = 100.0, position = 0, type = SetTypeDomain.WORK),
                        SetDomain("set-7b", reps = 0, weight = 100.0, position = 1, type = SetTypeDomain.WORK),
                    ),
                ),
            ),
        ).toUi(resources)

        assertEquals("100×5", ui.exercises.single().setSummary)
    }

    @Test
    fun `mapper marks a weighted set with no logged weight instead of printing bare reps`() {
        // A blank weight is valid input on this screen and persists as null. Printing the
        // bare rep count here would put "15" among "49×15" neighbours, where every leading
        // number is a weight — so the reps would read as kilograms.
        val ui = sessionDetail(
            isAdhoc = false,
            exercises = listOf(
                PerformedExerciseDetailDomain(
                    performedExerciseUuid = "performed-8",
                    exerciseUuid = "exercise-8",
                    exerciseName = "Bench",
                    exerciseType = ExerciseTypeDomain.WEIGHTED,
                    position = 0,
                    skipped = false,
                    sets = listOf(
                        SetDomain("set-8a", reps = 15, weight = 49.0, position = 0, type = SetTypeDomain.WORK),
                        SetDomain("set-8b", reps = 15, weight = null, position = 1, type = SetTypeDomain.WORK),
                    ),
                ),
            ),
        ).toUi(resources)

        assertEquals("49×15 · —×15", ui.exercises.single().setSummary)
    }

    // --- withExpansionCarriedFrom: the amended §7 model's seed-or-carry ------------------

    @Test
    fun `first Loaded state seeds exactly the first card open`() {
        val next = loadedUiState(exercises = listOf("pe-1", "pe-2", "pe-3"))
        val previous = State.create(sessionUuid = "session-1")

        val settled = with(PastSessionUiMapper) { next.withExpansionCarriedFrom(previous) }

        assertEquals(setOf("pe-1"), settled.expandedExerciseUuids)
    }

    @Test
    fun `first Loaded state with no exercises seeds nothing`() {
        val next = loadedUiState(exercises = emptyList())
        val previous = State.create(sessionUuid = "session-1")

        val settled = with(PastSessionUiMapper) { next.withExpansionCarriedFrom(previous) }

        assertEquals(emptySet<String>(), settled.expandedExerciseUuids)
    }

    @Test
    fun `Loaded to Loaded carries the previous open set pruned to live exercises`() {
        val previous = loadedUiState(exercises = listOf("pe-1", "pe-2", "pe-gone"))
            .copy(expandedExerciseUuids = persistentSetOf("pe-2", "pe-gone"))
        val next = loadedUiState(exercises = listOf("pe-1", "pe-2"))

        val settled = with(PastSessionUiMapper) { next.withExpansionCarriedFrom(previous) }

        assertEquals(setOf("pe-2"), settled.expandedExerciseUuids)
    }

    @Test
    fun `a non-Loaded next phase is returned untouched`() {
        val previous = loadedUiState(exercises = listOf("pe-1"))
            .copy(expandedExerciseUuids = persistentSetOf("pe-1"))
        val next = previous.copy(
            phase = State.Phase.Error(
                io.github.stslex.workeeper.feature.past_session.mvi.model.ErrorType.LoadFailed,
            ),
        )

        val settled = with(PastSessionUiMapper) { next.withExpansionCarriedFrom(previous) }

        assertEquals(next, settled)
    }

    private fun loadedUiState(exercises: List<String>): State =
        State.create(sessionUuid = "session-1").copy(
            phase = State.Phase.Loaded(
                detail = PastSessionUiModel(
                    trainingName = "Push Day",
                    isAdhoc = false,
                    finishedAtAbsoluteLabel = "Apr 28",
                    durationLabel = "01:00",
                    totalsLabel = "n · n",
                    exercises = exercises.map { uuid ->
                        PastExerciseUiModel(
                            performedExerciseUuid = uuid,
                            exerciseName = "Bench",
                            position = 0,
                            skipped = false,
                            isWeighted = true,
                            setSummary = "",
                            sets = persistentListOf(),
                        )
                    }.toImmutableList(),
                ),
            ),
        )

    private fun sessionDetail(
        isAdhoc: Boolean,
        exercises: List<PerformedExerciseDetailDomain>,
    ) = SessionDetailDomain(
        sessionUuid = "session-1",
        trainingUuid = "training-1",
        trainingName = "Push Day",
        isAdhoc = isAdhoc,
        startedAt = 0L,
        finishedAt = 90_000L,
        exercises = exercises,
    )

    private fun weightlessExercise(position: Int) = PerformedExerciseDetailDomain(
        performedExerciseUuid = "performed-1",
        exerciseUuid = "exercise-1",
        exerciseName = "Pull Up",
        exerciseType = ExerciseTypeDomain.WEIGHTLESS,
        position = position,
        skipped = false,
        sets = listOf(
            SetDomain(
                uuid = "set-1",
                reps = 10,
                weight = null,
                type = SetTypeDomain.WORK,
                position = 0,
            ),
        ),
    )

    private fun weightedExercise(position: Int) = PerformedExerciseDetailDomain(
        performedExerciseUuid = "performed-2",
        exerciseUuid = "exercise-2",
        exerciseName = "Bench",
        exerciseType = ExerciseTypeDomain.WEIGHTED,
        position = position,
        skipped = false,
        sets = listOf(
            SetDomain(
                uuid = "set-2",
                reps = 5,
                weight = 100.0,
                type = SetTypeDomain.WORK,
                position = 0,
            ),
            SetDomain(
                uuid = "set-3",
                reps = 3,
                weight = 90.0,
                type = SetTypeDomain.FAILURE,
                position = 1,
            ),
        ),
    )

    private fun skippedExercise(position: Int) = PerformedExerciseDetailDomain(
        performedExerciseUuid = "performed-4",
        exerciseUuid = "exercise-4",
        exerciseName = "Skipped Fly",
        exerciseType = ExerciseTypeDomain.WEIGHTED,
        position = position,
        skipped = true,
        sets = emptyList(),
    )
}
