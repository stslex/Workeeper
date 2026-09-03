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
import kotlin.math.roundToLong

internal object PastSessionUiMapper {

    private const val WEIGHT_DECIMAL_FACTOR = 10.0

    /**
     * Carries [State.expandedExerciseUuids] across a wholesale phase replacement: first entry
     * seeds the first card open, later emissions keep the open set, pruned to live exercises.
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
        // `reps <= 0` rows are not work; the count must agree with the live-session denominator.
        val totalSets = exercises.sumOf { exercise -> exercise.sets.count { it.reps > 0 } }
        val activeExercises = exercises.count { !it.skipped }
        val finishedAtLabel = resourceWrapper.formatMediumDate(finishedAt)
        val durationLabel = formatElapsedDuration(finishedAt - startedAt)
        val totalsLabel = buildTotalsLabel(
            resourceWrapper = resourceWrapper,
            exerciseCount = activeExercises,
            setCount = totalSets,
            tonnageKg = exercises.tonnageKg(),
        )
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

    /**
     * Session tonnage (spec §11.1). Mirrors `SessionDao.getBestSessionVolumes`: WEIGHTLESS
     * exercises are excluded wholesale, because residual non-null weights exist on their rows.
     */
    private fun List<PerformedExerciseDetailDomain>.tonnageKg(): Double = this
        .filter { exercise -> exercise.exerciseType == ExerciseTypeDomain.WEIGHTED }
        .sumOf { exercise ->
            exercise.sets.sumOf { set -> (set.weight ?: 0.0) * set.reps }
        }

    /**
     * "5 exercises · 14 sets · 4,820 kg". Two format strings so a session that lifted nothing
     * drops the third figure rather than reading "· 0 kg".
     */
    private fun buildTotalsLabel(
        resourceWrapper: ResourceWrapper,
        exerciseCount: Int,
        setCount: Int,
        tonnageKg: Double,
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
        val roundedTonnage = tonnageKg.roundToLong()
        if (roundedTonnage <= 0L) {
            return resourceWrapper.getString(
                R.string.feature_past_session_totals_format,
                exercises,
                sets,
            )
        }
        return resourceWrapper.getString(
            R.string.feature_past_session_totals_format_with_tonnage,
            exercises,
            sets,
            resourceWrapper.getString(R.string.feature_past_session_tonnage_format, roundedTonnage),
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
                    setSummary = exercise.setSummary(),
                    sets = exercise.sets.toUiSets(
                        performedExerciseUuid = exercise.performedExerciseUuid,
                        prSetUuids = prSetUuids,
                    ),
                )
            }
            .toImmutableList()

    /**
     * The collapsed card's summary line, `10×15 · 10×15`. Bare reps are a property of the
     * exercise TYPE; a weighted set with no logged weight keeps the `×` with [MISSING_WEIGHT].
     */
    private fun PerformedExerciseDetailDomain.setSummary(): String = sets
        .asSequence()
        .filter { set -> set.reps > 0 }
        .map { set ->
            if (exerciseType != ExerciseTypeDomain.WEIGHTED) {
                set.reps.toString()
            } else {
                val weight = set.weight?.let(::formatWeight) ?: MISSING_WEIGHT
                "$weight$SUMMARY_TIMES${set.reps}"
            }
        }
        .joinToString(separator = SUMMARY_SEPARATOR)

    private const val SUMMARY_SEPARATOR = " · "
    private const val SUMMARY_TIMES = "×"

    /** An em dash: a weighted set that logged no weight. */
    private const val MISSING_WEIGHT = "—"

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
