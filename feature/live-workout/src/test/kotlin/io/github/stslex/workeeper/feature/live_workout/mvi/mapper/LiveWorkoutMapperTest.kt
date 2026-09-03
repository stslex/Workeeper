// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.LiveExerciseDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PerformedExerciseDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionSnapshotDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionStateDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SetTypeDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toFinishStats
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toState
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toUiList
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.withExpansionCarriedFrom
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.DialogState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class LiveWorkoutMapperTest {
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)

    @Test
    fun `mapper marks the lowest-position non-skipped non-done exercise as CURRENT`() {
        val snapshot = SessionSnapshotDomain(
            session = sessionAt(1000L),
            trainingName = "Push Day",
            isAdhoc = false,
            exercises = listOf(
                fullyDone(uuid = "pe-1", position = 0),
                pending(uuid = "pe-2", position = 1),
                pending(uuid = "pe-3", position = 2),
            ),
            preSessionPrSnapshot = emptyMap(),
        )

        val state = snapshot.toState(
            nowMillis = 5000L,
            resourceWrapper = resourceWrapper,
            repsUnitLabel = REPS_UNIT,
        )

        assertEquals(ExerciseStatusUiModel.DONE, state.exercises[0].status)
        assertEquals(ExerciseStatusUiModel.CURRENT, state.exercises[1].status)
        assertEquals(ExerciseStatusUiModel.PENDING, state.exercises[2].status)
    }

    @Test
    fun `mapper marks skipped rows as SKIPPED and walks past them for CURRENT`() {
        val snapshot = SessionSnapshotDomain(
            session = sessionAt(1000L),
            trainingName = "Pull Day",
            isAdhoc = false,
            exercises = listOf(
                pending(
                    uuid = "pe-1",
                    position = 0,
                ).copy(performed = pending("pe-1", 0).performed.copy(skipped = true)),
                pending(uuid = "pe-2", position = 1),
            ),
            preSessionPrSnapshot = emptyMap(),
        )

        val state = snapshot.toState(
            nowMillis = 1000L,
            resourceWrapper = resourceWrapper,
            repsUnitLabel = REPS_UNIT,
        )

        assertEquals(ExerciseStatusUiModel.SKIPPED, state.exercises[0].status)
        assertEquals(ExerciseStatusUiModel.CURRENT, state.exercises[1].status)
    }

    @Test
    fun `first entry expands exactly the first card — even a completed one`() {
        // The amended disclosure model's whole initialisation rule (spec §7 superseded):
        // FIRST in the list, status not consulted.
        val snapshot = SessionSnapshotDomain(
            session = sessionAt(1000L),
            trainingName = "Push Day",
            isAdhoc = false,
            exercises = listOf(
                fullyDone(uuid = "pe-1", position = 0),
                pending(uuid = "pe-2", position = 1),
            ),
            preSessionPrSnapshot = emptyMap(),
        )

        val state = snapshot.toState(
            nowMillis = 1000L,
            resourceWrapper = resourceWrapper,
            repsUnitLabel = REPS_UNIT,
        )

        assertEquals(persistentSetOf("pe-1"), state.expandedExerciseUuids)
    }

    @Test
    fun `withExpansionCarriedFrom keeps the previous open set, pruned to live cards`() {
        val snapshot = SessionSnapshotDomain(
            session = sessionAt(1000L),
            trainingName = "Push Day",
            isAdhoc = false,
            exercises = listOf(
                pending(uuid = "pe-1", position = 0),
                pending(uuid = "pe-2", position = 1),
            ),
            preSessionPrSnapshot = emptyMap(),
        )
        val reloaded = snapshot.toState(
            nowMillis = 1000L,
            resourceWrapper = resourceWrapper,
            repsUnitLabel = REPS_UNIT,
        )
        val previous = reloaded.copy(
            expandedExerciseUuids = persistentSetOf("pe-2", "pe-deleted"),
        )

        val carried = reloaded.withExpansionCarriedFrom(previous)

        // The plan-editor round-trip fix survives the automaton's retirement: the user's
        // open set wins over the fresh first-card init, minus cards that no longer exist.
        assertEquals(persistentSetOf("pe-2"), carried.expandedExerciseUuids)
    }

    @Test
    fun `withExpansionCarriedFrom keeps the first-card init when nothing was loaded before`() {
        val snapshot = SessionSnapshotDomain(
            session = sessionAt(1000L),
            trainingName = "Push Day",
            isAdhoc = false,
            exercises = listOf(pending(uuid = "pe-1", position = 0)),
            preSessionPrSnapshot = emptyMap(),
        )
        val loaded = snapshot.toState(
            nowMillis = 1000L,
            resourceWrapper = resourceWrapper,
            repsUnitLabel = REPS_UNIT,
        )
        val freshStore = State.create(sessionUuid = "s", trainingUuid = "t")

        val carried = loaded.withExpansionCarriedFrom(freshStore)

        assertEquals(persistentSetOf("pe-1"), carried.expandedExerciseUuids)
    }

    @Test
    fun `REPRO a weighted plan set with no weight renders reps-only, never a fake zero`() {
        // Null weights are real: `PlanDraftReducer` writes them and a cleared weight field
        // parses to null, so a WEIGHTED exercise can legitimately carry reps-only plan
        // sets. The card subtitle must not invent a 0 kg target for them — the app's
        // established rendering (`PlanEditorUIMapper.formatPlanSummary`) is reps-only.
        val snapshot = SessionSnapshotDomain(
            session = sessionAt(1000L),
            trainingName = "Push Day",
            isAdhoc = false,
            exercises = listOf(
                pending(uuid = "pe-1", position = 0).copy(
                    planSets = listOf(
                        PlanSetDomain(weight = null, reps = 12, type = SetTypeDomain.WORK),
                        PlanSetDomain(weight = 60.0, reps = 8, type = SetTypeDomain.WORK),
                    ),
                ),
            ),
            preSessionPrSnapshot = emptyMap(),
        )

        val state = snapshot.toState(
            nowMillis = 1000L,
            resourceWrapper = resourceWrapper,
            repsUnitLabel = REPS_UNIT,
        )

        assertEquals("12 · 60×8", state.exercises.first().statusLabel)
    }

    /** The mapper is a pure synchronous transformation over an already-resolved localized unit. */
    @Test
    fun `WEIGHTLESS sublabel joins reps with the resolved localized unit`() {
        val snapshot = SessionSnapshotDomain(
            session = sessionAt(1000L),
            trainingName = "Push Day",
            isAdhoc = false,
            exercises = listOf(
                pending(uuid = "pe-1", position = 0).copy(
                    exerciseType = ExerciseTypeDomain.WEIGHTLESS,
                    planSets = listOf(
                        PlanSetDomain(weight = null, reps = 12, type = SetTypeDomain.WORK),
                        PlanSetDomain(weight = null, reps = 10, type = SetTypeDomain.WORK),
                    ),
                ),
            ),
            preSessionPrSnapshot = emptyMap(),
        )

        val enState = snapshot.toState(
            nowMillis = 1000L,
            resourceWrapper = resourceWrapper,
            repsUnitLabel = REPS_UNIT,
        )
        val ruState = snapshot.toState(
            nowMillis = 1000L,
            resourceWrapper = resourceWrapper,
            repsUnitLabel = "повт",
        )

        assertEquals("12 reps · 10 reps", enState.exercises.first().statusLabel)
        assertEquals("12 повт · 10 повт", ruState.exercises.first().statusLabel)
    }

    @Test
    fun `mapper preserves session start time on the resulting state`() {
        val snapshot = SessionSnapshotDomain(
            session = sessionAt(startedAt = 7_000L),
            trainingName = "Push Day",
            isAdhoc = false,
            exercises = emptyList(),
            preSessionPrSnapshot = emptyMap(),
        )

        val state = snapshot.toState(
            nowMillis = 9_000L,
            resourceWrapper = resourceWrapper,
            repsUnitLabel = REPS_UNIT,
        )

        assertEquals(7_000L, state.startedAt)
        assertEquals(9_000L, state.nowMillis)
        assertEquals(2_000L, state.elapsedMillis)
    }

    @Test
    fun `mapper treats no-plan exercise with logged sets as DONE`() {
        val snapshot = SessionSnapshotDomain(
            session = sessionAt(1000L),
            trainingName = "Adhoc",
            isAdhoc = true,
            exercises = listOf(
                pending(uuid = "pe-1", position = 0).copy(
                    planSets = null,
                    performedSets = listOf(
                        SetDomain(
                            uuid = "set-1",
                            weight = null,
                            reps = 10,
                            type = SetTypeDomain.WORK,
                            position = 0,
                        ),
                    ),
                ),
                pending(uuid = "pe-2", position = 1),
            ),
            preSessionPrSnapshot = emptyMap(),
        )

        val state = snapshot.toState(
            nowMillis = 2000L,
            resourceWrapper = resourceWrapper,
            repsUnitLabel = REPS_UNIT,
        )

        assertEquals(ExerciseStatusUiModel.DONE, state.exercises[0].status)
        assertEquals(ExerciseStatusUiModel.CURRENT, state.exercises[1].status)
    }

    private fun sessionAt(startedAt: Long): SessionDomain = SessionDomain(
        uuid = "session-1",
        trainingUuid = "training-1",
        state = SessionStateDomain.IN_PROGRESS,
        startedAt = startedAt,
        finishedAt = null,
    )

    private fun pending(uuid: String, position: Int): LiveExerciseDomain =
        LiveExerciseDomain(
            performed = PerformedExerciseDomain(
                uuid = uuid,
                sessionUuid = "session-1",
                exerciseUuid = "exercise-$position",
                position = position,
                skipped = false,
                exerciseName = "Exercise $position",
            ),
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            planSets = listOf(
                PlanSetDomain(weight = 100.0, reps = 5, type = SetTypeDomain.WORK),
                PlanSetDomain(weight = 100.0, reps = 5, type = SetTypeDomain.WORK),
            ),
            performedSets = emptyList(),
            isPlanAttached = true,
        )

    private fun fullyDone(uuid: String, position: Int): LiveExerciseDomain =
        pending(uuid, position).copy(
            performedSets = listOf(
                SetDomain(
                    uuid = "set-$uuid-0",
                    weight = 100.0,
                    reps = 5,
                    type = SetTypeDomain.WORK,
                    position = 0,
                ),
                SetDomain(
                    uuid = "set-$uuid-1",
                    weight = 100.0,
                    reps = 5,
                    type = SetTypeDomain.WORK,
                    position = 1,
                ),
            ),
        )

    @Test
    fun `toUiList without active uuids and no done exercises marks the first non-skipped as CURRENT`() {
        val ui = listOf(
            pending(uuid = "pe-1", position = 0),
            pending(uuid = "pe-2", position = 1),
        ).toUiList(activeUuids = emptySet())

        assertEquals(ExerciseStatusUiModel.CURRENT, ui[0].status)
        assertEquals(ExerciseStatusUiModel.PENDING, ui[1].status)
    }

    @Test
    fun `toUiList without active uuids skips done exercises and marks the next non-done as CURRENT`() {
        val ui = listOf(
            fullyDone(uuid = "pe-1", position = 0),
            pending(uuid = "pe-2", position = 1),
            pending(uuid = "pe-3", position = 2),
        ).toUiList(activeUuids = emptySet())

        assertEquals(ExerciseStatusUiModel.DONE, ui[0].status)
        assertEquals(ExerciseStatusUiModel.CURRENT, ui[1].status)
        assertEquals(ExerciseStatusUiModel.PENDING, ui[2].status)
    }

    @Test
    fun `toUiList with two uuids in active set marks both as CURRENT`() {
        val ui = listOf(
            pending(uuid = "pe-1", position = 0),
            pending(uuid = "pe-2", position = 1),
            pending(uuid = "pe-3", position = 2),
        ).toUiList(activeUuids = setOf("pe-1", "pe-3"))

        assertEquals(ExerciseStatusUiModel.CURRENT, ui[0].status)
        assertEquals(ExerciseStatusUiModel.PENDING, ui[1].status)
        assertEquals(ExerciseStatusUiModel.CURRENT, ui[2].status)
    }

    @Test
    fun `toUiList with one active uuid does not auto-promote any other PENDING exercise`() {
        val ui = listOf(
            pending(uuid = "pe-1", position = 0),
            pending(uuid = "pe-2", position = 1),
        ).toUiList(activeUuids = setOf("pe-2"))

        assertEquals(ExerciseStatusUiModel.PENDING, ui[0].status)
        assertEquals(ExerciseStatusUiModel.CURRENT, ui[1].status)
    }

    @Test
    fun `toUiList preserves SKIPPED and DONE regardless of active set`() {
        val ui = listOf(
            pending(uuid = "pe-1", position = 0).copy(
                performed = pending("pe-1", 0).performed.copy(skipped = true),
            ),
            fullyDone(uuid = "pe-2", position = 1),
            pending(uuid = "pe-3", position = 2),
        ).toUiList(activeUuids = setOf("pe-1", "pe-2", "pe-3"))

        assertEquals(ExerciseStatusUiModel.SKIPPED, ui[0].status)
        assertEquals(ExerciseStatusUiModel.DONE, ui[1].status)
        assertEquals(ExerciseStatusUiModel.CURRENT, ui[2].status)
    }

    @Test
    fun `toFinishStats emits one NewPrEntry per exercise that beats the snapshot`() {
        val res = mockk<ResourceWrapper>(relaxed = true)
        every {
            res.getString(R.string.feature_live_workout_finish_pr_weighted_format, "110", 5)
        } returns "110 × 5"
        every {
            res.getString(R.string.feature_live_workout_finish_pr_weightless_format, 18)
        } returns "18 reps"
        val state = State(
            sessionUuid = "s",
            trainingUuid = "t",
            trainingName = "X",
            trainingNameLabel = "X",
            trainingNameDraft = "X",
            isTrainingNameEditing = false,
            isAdhoc = false,
            startedAt = 0L,
            nowMillis = 0L,
            elapsedDurationLabel = "",
            doneCount = 2,
            totalCount = 2,
            setsLogged = 4,
            progress = 1f,
            headerMetaLabel = "",
            exercises = persistentListOf(
                exerciseUi(
                    exerciseUuid = "ex-1",
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    performed = listOf(
                        liveSet(0, 105.0, 5, isDone = true),
                        liveSet(1, 110.0, 5, isDone = true),
                    ),
                ),
                exerciseUi(
                    exerciseUuid = "ex-2",
                    name = "Pull-ups",
                    type = ExerciseTypeUiModel.WEIGHTLESS,
                    performed = listOf(liveSet(0, null, 18, isDone = true)),
                ),
            ),
            setDrafts = persistentMapOf(),
            activeExerciseUuids = persistentSetOf(),
            expandedExerciseUuids = persistentSetOf(),
            preSessionPrSnapshot = mapOf(
                "ex-1" to State.PrSnapshotItem(
                    weight = 100.0,
                    reps = 5,
                    type = ExerciseTypeUiModel.WEIGHTED,
                ),
                "ex-2" to State.PrSnapshotItem(
                    weight = null,
                    reps = 12,
                    type = ExerciseTypeUiModel.WEIGHTLESS,
                ),
            ).toImmutableMap(),
            isAddExerciseInFlight = false,
            isFinishInFlight = false,
            isLoading = false,
            loadFailed = false,
            dialogState = DialogState.Hidden,
            bottomSheetState = BottomSheetState.Hidden,
        )

        val stats = state.toFinishStats(res)

        assertEquals(2, stats.newPersonalRecords.size)
        assertEquals("ex-1", stats.newPersonalRecords[0].exerciseUuid)
        assertEquals("110 × 5", stats.newPersonalRecords[0].displayLabel)
        assertEquals("ex-2", stats.newPersonalRecords[1].exerciseUuid)
        assertEquals("18 reps", stats.newPersonalRecords[1].displayLabel)
    }

    @Test
    fun `toFinishStats emits no NewPrEntry when nothing beats the snapshot`() {
        val res = mockk<ResourceWrapper>(relaxed = true)
        val state = baseState().copy(
            exercises = persistentListOf(
                exerciseUi(
                    exerciseUuid = "ex-1",
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    performed = listOf(liveSet(0, 100.0, 5, isDone = true)),
                ),
            ),
            preSessionPrSnapshot = mapOf(
                "ex-1" to State.PrSnapshotItem(
                    weight = 100.0,
                    reps = 5,
                    type = ExerciseTypeUiModel.WEIGHTED,
                ),
            ).toImmutableMap(),
        )

        assertTrue(state.toFinishStats(res).newPersonalRecords.isEmpty())
    }

    @Test
    fun `toFinishStats excludes a skipped exercise even if performed sets exist`() {
        val res = mockk<ResourceWrapper>(relaxed = true)
        val state = baseState().copy(
            exercises = persistentListOf(
                exerciseUi(
                    exerciseUuid = "ex-1",
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    performed = listOf(liveSet(0, 200.0, 10, isDone = true)),
                    status = ExerciseStatusUiModel.SKIPPED,
                ),
            ),
            preSessionPrSnapshot = persistentMapOf(),
        )

        assertTrue(state.toFinishStats(res).newPersonalRecords.isEmpty())
    }

    @Test
    fun `toFinishStats excludes an exercise with no done sets`() {
        val res = mockk<ResourceWrapper>(relaxed = true)
        val state = baseState().copy(
            exercises = persistentListOf(
                exerciseUi(
                    exerciseUuid = "ex-1",
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    performed = listOf(liveSet(0, 200.0, 10, isDone = false)),
                ),
            ),
            preSessionPrSnapshot = persistentMapOf(),
        )

        assertTrue(state.toFinishStats(res).newPersonalRecords.isEmpty())
    }

    @Test
    fun `toFinishStats does not emit when the best set ties the snapshot exactly`() {
        val res = mockk<ResourceWrapper>(relaxed = true)
        val state = baseState().copy(
            exercises = persistentListOf(
                exerciseUi(
                    exerciseUuid = "ex-1",
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    performed = listOf(liveSet(0, 100.0, 5, isDone = true)),
                ),
            ),
            preSessionPrSnapshot = mapOf(
                "ex-1" to State.PrSnapshotItem(
                    weight = 100.0,
                    reps = 5,
                    type = ExerciseTypeUiModel.WEIGHTED,
                ),
            ).toImmutableMap(),
        )

        assertTrue(state.toFinishStats(res).newPersonalRecords.isEmpty())
    }

    @Test
    fun `toFinishStats picks the heaviest set per exercise even with multiple PR sets`() {
        val res = mockk<ResourceWrapper>(relaxed = true)
        every {
            res.getString(R.string.feature_live_workout_finish_pr_weighted_format, "115", 3)
        } returns "115 × 3"
        val state = baseState().copy(
            exercises = persistentListOf(
                exerciseUi(
                    exerciseUuid = "ex-1",
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    performed = listOf(
                        liveSet(0, 105.0, 5, isDone = true),
                        liveSet(1, 110.0, 5, isDone = true),
                        liveSet(2, 115.0, 3, isDone = true),
                    ),
                ),
            ),
            preSessionPrSnapshot = mapOf(
                "ex-1" to State.PrSnapshotItem(
                    weight = 100.0,
                    reps = 5,
                    type = ExerciseTypeUiModel.WEIGHTED,
                ),
            ).toImmutableMap(),
        )

        val stats = state.toFinishStats(res)

        assertEquals(1, stats.newPersonalRecords.size)
        assertEquals("115 × 3", stats.newPersonalRecords.single().displayLabel)
    }

    private fun baseState() = State(
        sessionUuid = "s",
        trainingUuid = "t",
        trainingName = "",
        trainingNameLabel = "",
        trainingNameDraft = "",
        isTrainingNameEditing = false,
        isAdhoc = false,
        startedAt = 0L,
        nowMillis = 0L,
        elapsedDurationLabel = "",
        doneCount = 0,
        totalCount = 0,
        setsLogged = 0,
        progress = 0f,
        headerMetaLabel = "",
        exercises = persistentListOf(),
        setDrafts = persistentMapOf(),
        activeExerciseUuids = persistentSetOf(),
        expandedExerciseUuids = persistentSetOf(),
        preSessionPrSnapshot = persistentMapOf(),
        isAddExerciseInFlight = false,
        isFinishInFlight = false,
        isLoading = false,
        loadFailed = false,
        dialogState = DialogState.Hidden,
        bottomSheetState = BottomSheetState.Hidden,
    )

    private fun exerciseUi(
        exerciseUuid: String,
        name: String,
        type: ExerciseTypeUiModel,
        performed: List<LiveSetUiModel>,
        status: ExerciseStatusUiModel = ExerciseStatusUiModel.DONE,
    ): LiveExerciseUiModel = LiveExerciseUiModel(
        performedExerciseUuid = "pe-$exerciseUuid",
        exerciseUuid = exerciseUuid,
        exerciseName = name,
        exerciseType = type,
        position = 0,
        status = status,
        statusLabel = "",
        planSets = persistentListOf<PlanSetUiModel>(),
        performedSets = performed.toImmutableList(),
    )

    private fun liveSet(
        position: Int,
        weight: Double?,
        reps: Int,
        isDone: Boolean,
    ): LiveSetUiModel = LiveSetUiModel(
        position = position,
        weight = weight,
        reps = reps,
        type = SetTypeUiModel.WORK,
        isDone = isDone,
    )

    private companion object {

        /** What the load boundary resolves in the default locale; the mapper never resolves it. */
        const val REPS_UNIT = "reps"
    }
}
