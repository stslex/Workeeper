// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorUIMapper.formatPlanSummary
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.domain.mapper.LiveWorkoutDomainMapper.beatsBaseline
import io.github.stslex.workeeper.feature.live_workout.domain.mapper.LiveWorkoutDomainMapper.bestOfDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.LiveExerciseDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PersonalRecordDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionSnapshotDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SetTypeDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveSetRowsResolver.withVisibleSets
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.DialogState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet

@Suppress("TooManyFunctions")
internal object LiveWorkoutMapper {

    fun SessionSnapshotDomain.toState(
        nowMillis: Long,
        resourceWrapper: ResourceWrapper,
    ): State {
        val typeByUuid = exercises.associate {
            it.performed.exerciseUuid to it.exerciseType
        }
        val prSnapshot = preSessionPrSnapshot.toUiSnapshot(typeByUuid)
        val ui = exercises.toUiList(prSnapshot = preSessionPrSnapshot, activeUuids = emptySet())
        val activeExerciseUuids = ui.filter { it.status == ExerciseStatusUiModel.CURRENT }
            .map { it.performedExerciseUuid }
            .toImmutableSet()

        return State(
            sessionUuid = session.uuid,
            trainingUuid = session.trainingUuid,
            trainingName = trainingName,
            trainingNameLabel = "",
            trainingNameDraft = trainingName,
            isTrainingNameEditing = false,
            isAdhoc = isAdhoc,
            startedAt = session.startedAt,
            nowMillis = nowMillis.coerceAtLeast(session.startedAt),
            elapsedDurationLabel = formatElapsedDuration(nowMillis - session.startedAt),
            doneCount = 0,
            totalCount = 0,
            setsLogged = 0,
            progress = 0f,
            progressLabel = "",
            exercises = ui,
            setDrafts = emptyMap<State.DraftKey, LiveSetUiModel>().toImmutableMap(),
            activeExerciseUuids = activeExerciseUuids,
            expandedExerciseUuids = activeExerciseUuids,
            preSessionPrSnapshot = prSnapshot,
            isAddExerciseInFlight = false,
            isFinishInFlight = false,
            isLoading = false,
            dialogState = DialogState.Hidden,
            bottomSheetState = BottomSheetState.Hidden,
        ).withPresentation(resourceWrapper)
    }

    fun List<LiveExerciseDomain>.toUiList(
        prSnapshot: Map<String, PersonalRecordDomain?> = emptyMap(),
        activeUuids: Set<String> = emptySet(),
    ): ImmutableList<LiveExerciseUiModel> {
        val sorted = sortedBy { it.performed.position }
        val computed = sorted.map { snapshot ->
            val plan = snapshot.planSets.orEmpty().map { it.toUi() }.toImmutableList()
            val baseline = prSnapshot[snapshot.performed.exerciseUuid]
            val performed = snapshot.toLiveSets(baseline)
            val isDone = isExerciseDone(plan, performed, snapshot.performed.skipped)
            Computed(snapshot, plan, performed, isDone)
        }
        val autoCurrentUuid = if (activeUuids.isEmpty()) {
            computed.firstOrNull { !it.snapshot.performed.skipped && !it.isDone }
                ?.snapshot?.performed?.uuid
        } else null

        return computed.map { c ->
            val uuid = c.snapshot.performed.uuid
            val status = when {
                c.snapshot.performed.skipped -> ExerciseStatusUiModel.SKIPPED
                c.isDone -> ExerciseStatusUiModel.DONE
                uuid in activeUuids || uuid == autoCurrentUuid -> ExerciseStatusUiModel.CURRENT
                else -> ExerciseStatusUiModel.PENDING
            }
            LiveExerciseUiModel(
                performedExerciseUuid = uuid,
                exerciseUuid = c.snapshot.performed.exerciseUuid,
                exerciseName = c.snapshot.performed.exerciseName,
                exerciseType = c.snapshot.exerciseType.toUi(),
                position = c.snapshot.performed.position,
                status = status,
                statusLabel = "",
                planSets = c.plan,
                performedSets = c.performed,
            )
        }.toImmutableList()
    }

    private data class Computed(
        val snapshot: LiveExerciseDomain,
        val plan: ImmutableList<PlanSetUiModel>,
        val performed: ImmutableList<LiveSetUiModel>,
        val isDone: Boolean,
    )

    private fun LiveExerciseDomain.toLiveSets(
        baseline: PersonalRecordDomain?,
    ): ImmutableList<LiveSetUiModel> =
        performedSets
            .map { set ->
                LiveSetUiModel(
                    position = set.position,
                    weight = set.weight,
                    reps = set.reps,
                    type = set.type.toUi(),
                    isDone = true,
                    isPersonalRecord = set.toPlanSetDomain().beatsBaseline(
                        baselineWeight = baseline?.weight,
                        baselineReps = baseline?.reps,
                        type = exerciseType,
                        hasBaseline = baseline != null,
                    ),
                )
            }
            .sortedBy { it.position }
            .toImmutableList()

    private fun SetDomain.toPlanSetDomain(): PlanSetDomain = PlanSetDomain(
        weight = weight,
        reps = reps,
        type = type,
    )

    private fun Map<String, PersonalRecordDomain?>.toUiSnapshot(
        typeByUuid: Map<String, ExerciseTypeDomain>,
    ): ImmutableMap<String, State.PrSnapshotItem> = entries
        .mapNotNull { (uuid, pr) ->
            pr?.let {
                uuid to State.PrSnapshotItem(
                    weight = pr.weight,
                    reps = pr.reps,
                    type = (typeByUuid[uuid] ?: ExerciseTypeDomain.WEIGHTED).toUi(),
                )
            }
        }
        .toMap()
        .toImmutableMap()

    private fun isExerciseDone(
        plan: ImmutableList<PlanSetUiModel>,
        performed: ImmutableList<LiveSetUiModel>,
        skipped: Boolean,
    ): Boolean {
        if (skipped) return false
        if (plan.isEmpty()) return performed.any { it.isDone }
        if (performed.size < plan.size) return false
        val performedByPosition = performed.associateBy { it.position }
        return plan.indices.all { idx -> performedByPosition[idx]?.isDone == true }
    }

    fun State.withPresentation(resourceWrapper: ResourceWrapper): State {
        // Recompute visible-row resolution alongside the rest of presentation. Every
        // `withPresentation` call now lands a fresh `visibleSets` list on each
        // exercise so handlers don't have to remember to refresh it independently.
        val withVisible = withVisibleSets()
        val presentedExercises = withVisible.exercises.map { exercise ->
            exercise.copy(statusLabel = exercise.toStatusLabel(resourceWrapper))
        }.toImmutableList()
        val doneCount = presentedExercises.count { it.status == ExerciseStatusUiModel.DONE }
        val totalCount = presentedExercises.size
        val setsLogged =
            presentedExercises.sumOf { exercise -> exercise.performedSets.count { it.isDone } }
        val safeTotal = totalCount.coerceAtLeast(1)
        val progress = (doneCount.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
        val setCountLabel = resourceWrapper.getQuantityString(
            R.plurals.feature_live_workout_set_count,
            setsLogged,
            setsLogged,
        )
        return withVisible.copy(
            trainingNameLabel = trainingName.ifBlank {
                resourceWrapper.getString(R.string.feature_live_workout_training_name_placeholder)
            },
            doneCount = doneCount,
            totalCount = totalCount,
            setsLogged = setsLogged,
            progress = progress,
            progressLabel = resourceWrapper.getString(
                R.string.feature_live_workout_progress_format,
                doneCount,
                totalCount,
                setCountLabel,
            ),
            exercises = presentedExercises,
        )
    }

    fun State.toFinishStats(resourceWrapper: ResourceWrapper): DialogState.FinishSession {
        val skippedCount = exercises.count { it.status == ExerciseStatusUiModel.SKIPPED }
        return DialogState.FinishSession(
            durationMillis = elapsedMillis,
            durationLabel = elapsedDurationLabel,
            exercisesSummaryLabel = formatExerciseSummary(
                resourceWrapper = resourceWrapper,
                doneCount = doneCount,
                totalCount = totalCount,
                skippedCount = skippedCount,
            ),
            setsLoggedLabel = resourceWrapper.getString(
                R.string.feature_live_workout_finish_stat_sets_count,
                setsLogged,
            ),
            newPersonalRecords = computeNewPersonalRecords(resourceWrapper),
            requiresName = trainingName.isBlank(),
            nameDraft = trainingName,
            nameLabel = resourceWrapper.getString(R.string.feature_live_workout_finish_name_label),
            namePlaceholder = resourceWrapper.getString(R.string.feature_live_workout_training_name_placeholder),
            nameError = null,
            confirmEnabled = trainingName.isNotBlank(),
        )
    }

    private fun State.computeNewPersonalRecords(
        resourceWrapper: ResourceWrapper,
    ): ImmutableList<DialogState.FinishSession.NewPrEntry> = exercises
        .asSequence()
        .filter { it.status != ExerciseStatusUiModel.SKIPPED }
        .mapNotNull { it.toNewPrEntry(preSessionPrSnapshot, resourceWrapper) }
        .toList()
        .toImmutableList()

    private fun LiveExerciseUiModel.toNewPrEntry(
        snapshot: Map<String, State.PrSnapshotItem>,
        resourceWrapper: ResourceWrapper,
    ): DialogState.FinishSession.NewPrEntry? {
        val performedAsPlanSets = performedSets
            .filter { it.isDone }
            .map { it.toPlanSetDomain() }
        if (performedAsPlanSets.isEmpty()) return null
        val typeDomain = exerciseType.toDomain()
        val best = bestOfDomain(performedAsPlanSets, typeDomain) ?: return null
        val baseline = snapshot[exerciseUuid]
        val beatsBaseline = best.beatsBaseline(
            baselineWeight = baseline?.weight,
            baselineReps = baseline?.reps,
            type = typeDomain,
            hasBaseline = baseline != null,
        )
        if (!beatsBaseline) return null
        return DialogState.FinishSession.NewPrEntry(
            exerciseUuid = exerciseUuid,
            exerciseName = exerciseName,
            displayLabel = formatPrLabel(
                weight = best.weight,
                reps = best.reps,
                type = exerciseType,
                resourceWrapper = resourceWrapper,
            ),
        )
    }

    fun LiveSetUiModel.toPlanSetDomain(): PlanSetDomain = PlanSetDomain(
        weight = weight,
        reps = reps,
        type = type.toDomain(),
    )

    fun PlanSetUiModel.toPlanSetDomain(): PlanSetDomain = PlanSetDomain(
        weight = weight,
        reps = reps,
        type = type.toDomain(),
    )

    fun PlanSetDomain.toUi(): PlanSetUiModel = PlanSetUiModel(
        weight = weight,
        reps = reps,
        type = type.toUi(),
    )

    fun List<PlanSetDomain>.toUi(): ImmutableList<PlanSetUiModel> =
        map { it.toUi() }.toImmutableList()

    fun SetTypeDomain.toUi(): SetTypeUiModel = when (this) {
        SetTypeDomain.WARMUP -> SetTypeUiModel.WARMUP
        SetTypeDomain.WORK -> SetTypeUiModel.WORK
        SetTypeDomain.FAILURE -> SetTypeUiModel.FAILURE
        SetTypeDomain.DROP -> SetTypeUiModel.DROP
    }

    fun SetTypeUiModel.toDomain(): SetTypeDomain = when (this) {
        SetTypeUiModel.WARMUP -> SetTypeDomain.WARMUP
        SetTypeUiModel.WORK -> SetTypeDomain.WORK
        SetTypeUiModel.FAILURE -> SetTypeDomain.FAILURE
        SetTypeUiModel.DROP -> SetTypeDomain.DROP
    }

    fun ExerciseTypeDomain.toUi(): ExerciseTypeUiModel = when (this) {
        ExerciseTypeDomain.WEIGHTED -> ExerciseTypeUiModel.WEIGHTED
        ExerciseTypeDomain.WEIGHTLESS -> ExerciseTypeUiModel.WEIGHTLESS
    }

    fun ExerciseTypeUiModel.toDomain(): ExerciseTypeDomain = when (this) {
        ExerciseTypeUiModel.WEIGHTED -> ExerciseTypeDomain.WEIGHTED
        ExerciseTypeUiModel.WEIGHTLESS -> ExerciseTypeDomain.WEIGHTLESS
    }

    private fun formatPrLabel(
        weight: Double?,
        reps: Int,
        type: ExerciseTypeUiModel,
        resourceWrapper: ResourceWrapper,
    ): String = when (type) {
        ExerciseTypeUiModel.WEIGHTED -> {
            val weightLabel = (weight ?: 0.0).formatPrWeight()
            resourceWrapper.getString(
                R.string.feature_live_workout_finish_pr_weighted_format,
                weightLabel,
                reps,
            )
        }

        ExerciseTypeUiModel.WEIGHTLESS -> resourceWrapper.getString(
            R.string.feature_live_workout_finish_pr_weightless_format,
            reps,
        )
    }

    private fun Double.formatPrWeight(): String = if (this % 1.0 == 0.0) {
        toLong().toString()
    } else {
        toString().trimEnd('0').trimEnd('.')
    }

    private fun LiveExerciseUiModel.toStatusLabel(resourceWrapper: ResourceWrapper): String =
        when (status) {
            ExerciseStatusUiModel.DONE -> {
                val count = performedSets.count { it.isDone }
                val setCountLabel = resourceWrapper.getQuantityString(
                    R.plurals.feature_live_workout_status_set_count,
                    count,
                    count,
                )
                resourceWrapper.getString(
                    R.string.feature_live_workout_status_completed_format,
                    setCountLabel,
                )
            }

            ExerciseStatusUiModel.CURRENT -> {
                if (planSets.isEmpty()) {
                    resourceWrapper.getString(R.string.feature_live_workout_status_no_plan)
                } else if (performedSets.none { it.isDone }) {
                    resourceWrapper.getString(
                        R.string.feature_live_workout_status_plan_format,
                        planSets.formatPlanSummary(),
                    )
                } else {
                    resourceWrapper.getString(
                        R.string.feature_live_workout_status_progress_format,
                        performedSets.count { it.isDone },
                        planSets.size,
                    )
                }
            }

            ExerciseStatusUiModel.PENDING -> {
                val summary = if (planSets.isEmpty()) {
                    resourceWrapper.getString(R.string.feature_live_workout_status_no_plan)
                } else {
                    planSets.formatPlanSummary()
                }
                resourceWrapper.getString(R.string.feature_live_workout_status_plan_format, summary)
            }

            ExerciseStatusUiModel.SKIPPED -> resourceWrapper.getString(R.string.feature_live_workout_status_skipped)
        }

    private fun formatExerciseSummary(
        resourceWrapper: ResourceWrapper,
        doneCount: Int,
        totalCount: Int,
        skippedCount: Int,
    ): String = if (skippedCount > 0) {
        resourceWrapper.getString(
            R.string.feature_live_workout_finish_stat_exercises_with_skipped_format,
            doneCount,
            totalCount,
            skippedCount,
        )
    } else {
        resourceWrapper.getString(
            R.string.feature_live_workout_finish_stat_exercises_format,
            doneCount,
            totalCount,
        )
    }
}
