// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge

import dev.zacsweers.metro.Inject
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.wear.protocol.BoundedDisplayName
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.MutationUnavailableReason
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.OutlineBudget
import io.github.stslex.workeeper.core.wear.protocol.OutlineExercise
import io.github.stslex.workeeper.core.wear.protocol.OutlineSet
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import java.math.BigDecimal
import kotlin.uuid.Uuid

/**
 * Derives the session outline only from persisted phone rows, inside the caller's transaction
 * (wear-phase-2-exercise-list.md §6.1). A phone draft or a row-count override is never read.
 */
internal class PhoneWorkoutSnapshotBuilder @Inject constructor(
    private val database: AppDatabase,
) {

    /** The outline within the budget of §5.7, or `PayloadTooLarge` of its session. */
    suspend fun build(databaseEpoch: CanonicalUuid): SnapshotData {
        val session = database.sessionDao.getActive()
            ?: return SnapshotData(databaseEpoch, SnapshotPayload.NoSession)
        val training = requireNotNull(database.trainingDao.getById(session.trainingUuid))
        val performed = database.performedExerciseDao.getBySession(session.uuid)
            .sortedBy(PerformedExerciseEntity::position)
        val exercises = database.exerciseDao
            .getByUuids(performed.map(PerformedExerciseEntity::exerciseUuid).distinct())
            .associateBy(ExerciseEntity::uuid)
        val setsByPerformed = if (performed.isEmpty()) {
            emptyMap()
        } else {
            database.setDao.getByPerformedExercises(performed.map(PerformedExerciseEntity::uuid))
                .groupBy(SetEntity::performedExerciseUuid)
        }
        val plans = loadPlans(training, performed)
        val outline = performed.map { row ->
            val exercise = requireNotNull(exercises[row.exerciseUuid])
            outlineExercise(
                row = row,
                exercise = exercise,
                plan = plans[row.exerciseUuid].orEmpty(),
                sets = setsByPerformed[row.uuid].orEmpty(),
            )
        }
        return OutlineBudget.bounded(
            SnapshotData(
                databaseEpoch = databaseEpoch,
                payload = SnapshotPayload.Active(
                    sessionUuid = session.uuid.toWireUuid(),
                    sessionRevision = session.wearRevision,
                    trainingName = BoundedDisplayName.from(training.name),
                    exercises = outline,
                    mutationAuthority = MutationAuthority.Unavailable(
                        MutationUnavailableReason.FRESH_HANDSHAKE_REQUIRED,
                    ),
                ),
            ),
        )
    }

    /**
     * The expected positions are plan indices ∪ performed positions (Phase 1 §4). A performed one is
     * `Done` with each value converted on its own; any other is `Open` from its plan row, or
     * `Blocked` when a planned value is outside the watch domain.
     */
    private fun outlineExercise(
        row: PerformedExerciseEntity,
        exercise: ExerciseEntity,
        plan: List<PlanSetDataModel>,
        sets: List<SetEntity>,
    ): OutlineExercise {
        val performedByPosition = sets.associateBy(SetEntity::position)
        val expectedPositions = buildSet {
            addAll(plan.indices)
            addAll(performedByPosition.keys)
        }.sorted()
        val outlineSets = expectedPositions.map { position ->
            val performedSet = performedByPosition[position]
            if (performedSet != null) {
                OutlineSet.Done(
                    position = position,
                    setType = performedSet.type.toWire(),
                    reps = snapshotReps(performedSet.reps),
                    weightHundredthsKg = (snapshotWeight(exercise.type, performedSet.weight) as? SnapshotWeight.Value)
                        ?.hundredths,
                )
            } else {
                val planSet = requireNotNull(plan.getOrNull(position)) {
                    "An incomplete persisted position must originate from a plan position"
                }
                when (val numeric = snapshotNumeric(exercise.type, planSet.reps, planSet.weight)) {
                    is SnapshotNumeric.Value -> OutlineSet.Open(
                        position = position,
                        setType = planSet.type.toWire(),
                        reps = numeric.reps,
                        weightHundredthsKg = numeric.weightHundredthsKg,
                    )
                    is SnapshotNumeric.Unsupported -> OutlineSet.Blocked(
                        position = position,
                        setType = planSet.type.toWire(),
                        field = numeric.field,
                    )
                }
            }
        }
        return OutlineExercise(
            performedExerciseUuid = row.uuid.toWireUuid(),
            name = BoundedDisplayName.from(exercise.name),
            exerciseType = exercise.type.toWire(),
            skipped = row.skipped,
            sets = outlineSets,
        )
    }

    private suspend fun loadPlans(
        training: TrainingEntity,
        performed: List<PerformedExerciseEntity>,
    ): Map<Uuid, List<PlanSetDataModel>?> {
        if (performed.isEmpty()) return emptyMap()
        val exerciseUuids = performed.map(PerformedExerciseEntity::exerciseUuid).distinct()
        val trainingPlans = if (training.isAdhoc) {
            emptyMap()
        } else {
            database.trainingExerciseDao
                .getPlanSetsBatch(training.uuid, exerciseUuids)
                .associate { it.exerciseUuid to PlanSetsConverter.fromJson(it.planSets) }
        }
        val fallbackUuids = if (training.isAdhoc) {
            exerciseUuids
        } else {
            exerciseUuids.filter { trainingPlans[it] == null }
        }
        val fallbackPlans = if (fallbackUuids.isEmpty()) {
            emptyMap()
        } else {
            database.exerciseDao.getAdhocPlansBatch(fallbackUuids)
                .associate { it.uuid to PlanSetsConverter.fromJson(it.lastAdhocSets) }
        }
        return exerciseUuids.associateWith { uuid ->
            if (training.isAdhoc) fallbackPlans[uuid] else trainingPlans[uuid] ?: fallbackPlans[uuid]
        }
    }
}

internal sealed interface SnapshotNumeric {
    data class Value(val reps: Int, val weightHundredthsKg: Int?) : SnapshotNumeric
    data class Unsupported(val field: NumericField) : SnapshotNumeric
}

/** The watch-domain value of one weight: null for a weightless exercise or no weight. */
internal sealed interface SnapshotWeight {
    data class Value(val hundredths: Int?) : SnapshotWeight
    data object Unsupported : SnapshotWeight
}

/** Both planned values at once; reps are checked before the weight. */
internal fun snapshotNumeric(
    exerciseType: ExerciseTypeEntity,
    reps: Int,
    weight: Double?,
): SnapshotNumeric {
    val wearReps = snapshotReps(reps) ?: return SnapshotNumeric.Unsupported(NumericField.REPS)
    return when (val wearWeight = snapshotWeight(exerciseType, weight)) {
        is SnapshotWeight.Value -> SnapshotNumeric.Value(wearReps, wearWeight.hundredths)
        SnapshotWeight.Unsupported -> SnapshotNumeric.Unsupported(NumericField.WEIGHT)
    }
}

/** [reps] when it is inside the watch domain, else null. */
internal fun snapshotReps(reps: Int): Int? = reps.takeIf { it in 0..WearProtocol.MAX_WEAR_REPS }

/** The exact hundredths of [weight] inside the watch domain; no weight for a weightless exercise. */
internal fun snapshotWeight(exerciseType: ExerciseTypeEntity, weight: Double?): SnapshotWeight {
    if (exerciseType == ExerciseTypeEntity.WEIGHTLESS) return SnapshotWeight.Value(hundredths = null)
    weight ?: return SnapshotWeight.Value(hundredths = null)
    if (!weight.isFinite() || weight < 0.0 || weight.isNegativeZero()) return SnapshotWeight.Unsupported
    val hundredths = runCatching {
        BigDecimal.valueOf(weight).movePointRight(2).intValueExact()
    }.getOrNull() ?: return SnapshotWeight.Unsupported
    if (hundredths !in 0..WearProtocol.MAX_WEAR_WEIGHT_HUNDREDTHS_KG ||
        hundredths.toDouble() / HUNDREDTHS_PER_KG != weight
    ) {
        return SnapshotWeight.Unsupported
    }
    return SnapshotWeight.Value(hundredths)
}

internal fun Uuid.toWireUuid(): CanonicalUuid = CanonicalUuid.parse(toString())

internal fun ExerciseTypeEntity.toWire(): ExerciseTypeWire = when (this) {
    ExerciseTypeEntity.WEIGHTED -> ExerciseTypeWire.WEIGHTED
    ExerciseTypeEntity.WEIGHTLESS -> ExerciseTypeWire.WEIGHTLESS
}

internal fun SetTypeEntity.toWire(): SetTypeWire = when (this) {
    SetTypeEntity.WARM -> SetTypeWire.WARM
    SetTypeEntity.WORK -> SetTypeWire.WORK
    SetTypeEntity.FAIL -> SetTypeWire.FAIL
    SetTypeEntity.DROP -> SetTypeWire.DROP
}

internal fun SetTypeDataModel.toWire(): SetTypeWire = when (this) {
    SetTypeDataModel.WARMUP -> SetTypeWire.WARM
    SetTypeDataModel.WORK -> SetTypeWire.WORK
    SetTypeDataModel.FAILURE -> SetTypeWire.FAIL
    SetTypeDataModel.DROP -> SetTypeWire.DROP
}

internal fun SnapshotPayload.sessionIdentityOrNull(): Pair<CanonicalUuid, Long>? = when (this) {
    SnapshotPayload.NoSession -> null
    is SnapshotPayload.Active -> sessionUuid to sessionRevision
    is SnapshotPayload.PayloadTooLarge -> sessionUuid to sessionRevision
}

private const val NEGATIVE_ZERO: Double = -0.0
private const val HUNDREDTHS_PER_KG: Double = 100.0

private fun Double.isNegativeZero(): Boolean =
    java.lang.Double.doubleToRawLongBits(this) ==
        java.lang.Double.doubleToRawLongBits(NEGATIVE_ZERO)
