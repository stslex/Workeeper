// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.past_session.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.PerformedExerciseDetailDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.SessionDetailDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.SetDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.SetTypeDomain
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastExerciseUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSessionUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSetUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import io.github.stslex.workeeper.feature.past_session.resources.Res
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_adhoc_label
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_exercises_count
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_sets_count
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_tonnage_format
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_tonnage_group_separator
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_totals_format
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_totals_format_with_tonnage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlin.math.roundToLong

internal object PastSessionUiMapper {

    private const val WEIGHT_DECIMAL_FACTOR = 10.0
    private const val THOUSANDS_GROUP_SIZE = 3

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

    suspend fun SessionDetailDomain.toUi(
        resourceWrapper: ResourceWrapper,
        stringResolver: StringResolver,
        prSetUuids: Set<String> = emptySet(),
    ): PastSessionUiModel {
        // `reps <= 0` rows are not work; the count must agree with the live-session denominator.
        val totalSets = exercises.sumOf { exercise -> exercise.sets.count { it.reps > 0 } }
        val activeExercises = exercises.count { !it.skipped }
        val finishedAtLabel = resourceWrapper.formatMediumDate(finishedAt)
        val durationLabel = formatElapsedDuration(finishedAt - startedAt)
        val totalsLabel = buildTotalsLabel(
            stringResolver = stringResolver,
            exerciseCount = activeExercises,
            setCount = totalSets,
            tonnageKg = exercises.tonnageKg(),
        )
        val trainingName = if (isAdhoc) {
            stringResolver.getString(Res.string.feature_past_session_adhoc_label)
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
    private suspend fun buildTotalsLabel(
        stringResolver: StringResolver,
        exerciseCount: Int,
        setCount: Int,
        tonnageKg: Double,
    ): String {
        val exercises = stringResolver.getPluralString(
            Res.plurals.feature_past_session_exercises_count,
            exerciseCount,
            exerciseCount,
        )
        val sets = stringResolver.getPluralString(
            Res.plurals.feature_past_session_sets_count,
            setCount,
            setCount,
        )
        val roundedTonnage = tonnageKg.roundToLong()
        if (roundedTonnage <= 0L) {
            return stringResolver.getString(
                Res.string.feature_past_session_totals_format,
                exercises,
                sets,
            )
        }
        val separator = stringResolver.getString(
            Res.string.feature_past_session_tonnage_group_separator,
        )
        return stringResolver.getString(
            Res.string.feature_past_session_totals_format_with_tonnage,
            exercises,
            sets,
            stringResolver.getString(
                Res.string.feature_past_session_tonnage_format,
                roundedTonnage.groupThousands(separator),
            ),
        )
    }

    /**
     * "4820" → "4,820": Compose resources cannot format `%,d`, so the figure is grouped here with
     * the catalog's [separator] — a comma in en, a no-break space in ru.
     */
    private fun Long.groupThousands(separator: String): String = toString()
        .reversed()
        .chunked(THOUSANDS_GROUP_SIZE)
        .map { it.reversed() }
        .reversed()
        .joinToString(separator)

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
