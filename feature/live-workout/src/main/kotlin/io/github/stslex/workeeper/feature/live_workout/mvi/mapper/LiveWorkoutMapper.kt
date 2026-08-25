// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
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
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import io.github.stslex.workeeper.core.ui.kit.R as KitR

@Suppress("TooManyFunctions")
internal object LiveWorkoutMapper {

    /** The mockup's mid-dot joiner, used by `.sub` (`planText`, session-v3f.html:282). */
    private const val SUB_SEPARATOR = " · "

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
            headerMetaLabel = "",
            exercises = ui,
            setDrafts = emptyMap<State.DraftKey, LiveSetUiModel>().toImmutableMap(),
            activeExerciseUuids = activeExerciseUuids,
            // First entry expands the FIRST card, whatever its status (amended §7).
            expandedExerciseUuids = ui.firstOrNull()
                ?.let { persistentSetOf(it.performedExerciseUuid) }
                ?: persistentSetOf(),
            preSessionPrSnapshot = prSnapshot,
            isAddExerciseInFlight = false,
            isFinishInFlight = false,
            isLoading = false,
            loadFailed = false,
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
            val isDone = ExerciseDoneRule.isDoneLoad(
                planSets = plan,
                performedSets = performed,
                skipped = snapshot.performed.skipped,
            )
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
                isPlanAttached = c.snapshot.isPlanAttached,
                description = c.snapshot.description,
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

    fun State.withPresentation(resourceWrapper: ResourceWrapper): State {
        // Refreshes `visibleSets` here so handlers never have to do it themselves.
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
        return withVisible.copy(
            trainingNameLabel = trainingName.ifBlank {
                resourceWrapper.getString(R.string.feature_live_workout_training_name_placeholder)
            },
            doneCount = doneCount,
            totalCount = totalCount,
            setsLogged = setsLogged,
            progress = progress,
            headerMetaLabel = presentedExercises.toHeaderMetaLabel(resourceWrapper),
            exercises = presentedExercises,
        )
    }

    /**
     * The `.shead` meta line; every denominator excludes skipped exercises, and an empty
     * session gets a blank line. See the screen-extraction spec §1.3.
     */
    private fun List<LiveExerciseUiModel>.toHeaderMetaLabel(
        resourceWrapper: ResourceWrapper,
    ): String {
        if (isEmpty()) return ""
        val nonSkipped = filter { it.status != ExerciseStatusUiModel.SKIPPED }
        val skippedCount = size - nonSkipped.size
        val base = resourceWrapper.getString(
            R.string.feature_live_workout_meta_format,
            nonSkipped.count { it.status == ExerciseStatusUiModel.DONE },
            nonSkipped.size,
            nonSkipped.sumOf { exercise -> exercise.visibleSets.count { it.isDone } },
            nonSkipped.sumOf { it.visibleSets.size },
        )
        return if (skippedCount > 0) {
            resourceWrapper.getString(
                R.string.feature_live_workout_meta_skipped_format,
                base,
                skippedCount,
            )
        } else {
            base
        }
    }

    /**
     * Carries the open set across a wholesale State replacement, so a re-fired `Init` does not
     * reset expansion. Only a genuinely fresh Store keeps first-card initialisation.
     */
    fun State.withExpansionCarriedFrom(previous: State): State {
        if (previous.exercises.isEmpty()) return this
        val liveUuids = exercises.mapTo(mutableSetOf()) { it.performedExerciseUuid }
        return copy(
            expandedExerciseUuids = previous.expandedExerciseUuids
                .filterTo(mutableSetOf()) { it in liveUuids }
                .toImmutableSet(),
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
            unfilledSetCount = unfilledSetCount,
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

    /**
     * The card's `.sub` line — always the plan, never a status readout, so the card's height
     * stays stable; only a skipped card substitutes `пропущено`. See extraction §1.5.
     */
    private fun LiveExerciseUiModel.toStatusLabel(resourceWrapper: ResourceWrapper): String =
        when {
            status == ExerciseStatusUiModel.SKIPPED ->
                resourceWrapper.getString(R.string.feature_live_workout_status_skipped)

            planSets.isEmpty() ->
                resourceWrapper.getString(R.string.feature_live_workout_status_no_plan)

            else -> planSets.toPlanSubLabel(resourceWrapper, exerciseType)
        }

    private fun List<PlanSetUiModel>.toPlanSubLabel(
        resourceWrapper: ResourceWrapper,
        exerciseType: ExerciseTypeUiModel,
    ): String = when (exerciseType) {
        // A WEIGHTED exercise can carry reps-only plan sets; substituting 0.0 would print a
        // 0 kg target the user never set.
        ExerciseTypeUiModel.WEIGHTED -> joinToString(SUB_SEPARATOR) { set ->
            set.weight
                ?.let { weight -> "${weight.formatPrWeight()}×${set.reps}" }
                ?: set.reps.toString()
        }

        ExerciseTypeUiModel.WEIGHTLESS -> {
            val unit = resourceWrapper.getString(KitR.string.core_ui_kit_plan_editor_unit_reps)
            joinToString(SUB_SEPARATOR) { set -> "${set.reps} $unit" }
        }
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
