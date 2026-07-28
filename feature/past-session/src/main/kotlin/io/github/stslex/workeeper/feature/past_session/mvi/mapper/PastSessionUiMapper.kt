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
     * Session tonnage — the third figure of the v3 header, restored by spec §11.1.
     *
     * **This reverses a deliberate v2.4 decision.** Commit `8a3f8192` ("v2.4 5.7") deleted the
     * equivalent `computeVolume` from this very mapper on the grounds that the per-set view
     * and the chart surface volume more usefully than one rolled-up number. §11.1 decides
     * otherwise and this is its implementation; the reasoning is not lost, it is overruled.
     *
     * ## The predicate is not `weight ?: 0.0` over everything
     *
     * It mirrors `SessionDao.getBestSessionVolumes` exactly — `e.type = 'WEIGHTED' AND
     * s.weight IS NOT NULL` — and the WEIGHTLESS clause is the load-bearing half.
     * `SetEntity.weight` is nullable but *not* type-constrained: residual non-null weights on
     * weightless rows exist in shipped data, and scrubbing them by migration was explicitly
     * rejected (spec §12) because it discards logged data irreversibly. A naive sum over
     * every set would quietly absorb those as kilograms. Excluding WEIGHTLESS exercises
     * wholesale is both the honest semantic — bodyweight work lifts no measured kg — and the
     * only reading that agrees with the app's other volume aggregate.
     *
     * No `reps > 0` clause is needed: a zero-rep set contributes a zero product either way.
     * Skipped exercises are *not* excluded, matching the set count this figure sits beside.
     */
    private fun List<PerformedExerciseDetailDomain>.tonnageKg(): Double = this
        .filter { exercise -> exercise.exerciseType == ExerciseTypeDomain.WEIGHTED }
        .sumOf { exercise ->
            exercise.sets.sumOf { set -> (set.weight ?: 0.0) * set.reps }
        }

    /**
     * "5 exercises · 14 sets · 4,820 kg".
     *
     * Two format strings rather than one with an empty third argument: a session that lifted
     * nothing — every exercise weightless, or a weighted session logged without weights —
     * would otherwise read "· 0 kg", which states a measurement that was never taken. The
     * figure simply drops out. Grouping is `%,d` in the resource, formatted against the
     * configuration locale — "4,820" in en, "4 820" in ru — without a number formatter this
     * codebase does not have.
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
