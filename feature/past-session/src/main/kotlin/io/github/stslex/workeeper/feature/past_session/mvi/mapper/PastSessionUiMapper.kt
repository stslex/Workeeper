// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.past_session.R
import io.github.stslex.workeeper.feature.past_session.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.PerformedExerciseDetailDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.SessionDetailDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.SetDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.SetTypeDomain
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastExerciseUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSessionUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSetUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

internal object PastSessionUiMapper {

    private const val WEIGHT_DECIMAL_FACTOR = 10.0

    /**
     * Settles [State.expandedExerciseUuids] across a wholesale phase replacement — the
     * amended §7 disclosure model's only two writers besides the header tap.
     *
     * `observeDetailWithPrs` re-emits on every PR-flow change (an edit that moves a record
     * re-fetches the whole detail), and each emission replaces the Loaded phase. Without this
     * carry, an edit round-trip would silently reset the user's open cards.
     *
     * - Previous phase not Loaded (first entry, or retry after an error): the FIRST card in
     *   the list is expanded. Status is not consulted — that is the whole initialisation
     *   rule, same as `LiveWorkoutMapper`'s first-entry seeding.
     * - Previous phase Loaded: the previous open set wins, pruned to exercises that still
     *   exist. Pruning is defensive — this screen cannot remove single exercises today, but
     *   a stale uuid surviving in the set would be an invisible leak, not a harmless one.
     */
    fun State.withExpansionCarriedFrom(previous: State): State {
        val loaded = phase as? State.Phase.Loaded ?: return this
        val previousLoaded = previous.phase as? State.Phase.Loaded
        return if (previousLoaded == null) {
            copy(
                expandedExerciseUuids = loaded.detail.exercises.firstOrNull()
                    ?.let { persistentSetOf(it.performedExerciseUuid) }
                    ?: persistentSetOf(),
            )
        } else {
            val liveUuids = loaded.detail.exercises
                .mapTo(mutableSetOf()) { it.performedExerciseUuid }
            copy(
                expandedExerciseUuids = previous.expandedExerciseUuids
                    .filterTo(mutableSetOf()) { it in liveUuids }
                    .toImmutableSet(),
            )
        }
    }

    fun SessionDetailDomain.toUi(
        resourceWrapper: ResourceWrapper,
        prSetUuids: Set<String> = emptySet(),
    ): PastSessionUiModel {
        // Unfilled rows (`reps <= 0`) are not work and must not inflate the session summary
        // (§6.1). No production writer can persist one today, so this is defence-in-depth
        // over legacy or imported data — the count must agree with the live-session
        // denominator, which excludes them.
        val totalSets = exercises.sumOf { exercise -> exercise.sets.count { it.reps > 0 } }
        val activeExercises = exercises.count { !it.skipped }
        val finishedAtLabel = resourceWrapper.formatMediumDate(finishedAt)
        val durationLabel = formatElapsedDuration(finishedAt - startedAt)
        val totalsLabel = buildTotalsLabel(resourceWrapper, activeExercises, totalSets)
        val trainingName = if (isAdhoc) {
            resourceWrapper.getString(R.string.feature_past_session_adhoc_label)
        } else {
            trainingName
        }
        return PastSessionUiModel(
            trainingName = trainingName,
            isAdhoc = isAdhoc,
            finishedAtAbsoluteLabel = finishedAtLabel,
            durationLabel = durationLabel,
            totalsLabel = totalsLabel,
            exercises = exercises.toUiList(prSetUuids),
        )
    }

    private fun buildTotalsLabel(
        resourceWrapper: ResourceWrapper,
        exerciseCount: Int,
        setCount: Int,
    ): String {
        val exercises = resourceWrapper.getQuantityString(
            R.plurals.feature_past_session_exercises_count,
            exerciseCount,
            exerciseCount,
        )
        val sets = resourceWrapper.getQuantityString(
            R.plurals.feature_past_session_sets_count,
            setCount,
            setCount,
        )
        return resourceWrapper.getString(
            R.string.feature_past_session_totals_format,
            exercises,
            sets,
        )
    }

    private fun List<PerformedExerciseDetailDomain>.toUiList(
        prSetUuids: Set<String>,
    ): ImmutableList<PastExerciseUiModel> =
        sortedBy { it.position }
            .map { exercise ->
                PastExerciseUiModel(
                    performedExerciseUuid = exercise.performedExerciseUuid,
                    exerciseName = exercise.exerciseName,
                    position = exercise.position,
                    skipped = exercise.skipped,
                    isWeighted = exercise.exerciseType == ExerciseTypeDomain.WEIGHTED,
                    sets = exercise.sets.toUiSets(
                        performedExerciseUuid = exercise.performedExerciseUuid,
                        prSetUuids = prSetUuids,
                    ),
                )
            }
            .toImmutableList()

    private fun List<SetDomain>.toUiSets(
        performedExerciseUuid: String,
        prSetUuids: Set<String>,
    ): ImmutableList<PastSetUiModel> = this
        .mapIndexed { index, set ->
            PastSetUiModel(
                setUuid = set.uuid,
                performedExerciseUuid = performedExerciseUuid,
                position = index,
                type = set.type.toUi(),
                weightInput = set.weight?.let(::formatWeight).orEmpty(),
                repsInput = set.reps.takeIf { it > 0 }?.toString().orEmpty(),
                weightError = false,
                repsError = false,
                isPersonalRecord = set.uuid in prSetUuids,
            )
        }
        .toImmutableList()

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

    private fun formatWeight(weight: Double): String {
        val rounded = (weight * WEIGHT_DECIMAL_FACTOR).toLong() / WEIGHT_DECIMAL_FACTOR
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}
