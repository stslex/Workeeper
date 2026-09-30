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
import io.github.stslex.workeeper.core.wear.protocol.ActiveTarget
import io.github.stslex.workeeper.core.wear.protocol.BoundedDisplayName
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.MutationUnavailableReason
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.PhoneActionReason
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import java.math.BigDecimal
import kotlin.uuid.Uuid

/** Derives the Phase-1 target only from persisted phone rows, inside the caller's transaction. */
internal class PhoneWorkoutSnapshotBuilder @Inject constructor(
    private val database: AppDatabase,
) {

    suspend fun build(databaseEpoch: CanonicalUuid): SnapshotData {
        val session = database.sessionDao.getActive()
            ?: return SnapshotData(databaseEpoch, SnapshotPayload.NoSession)
        val training = requireNotNull(database.trainingDao.getById(session.trainingUuid))
        val performed = database.performedExerciseDao.getBySession(session.uuid)
            .sortedBy(PerformedExerciseEntity::position)
            .filterNot(PerformedExerciseEntity::skipped)
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
        var completedExercises = 0

        performed.forEach { row ->
            val exercise = requireNotNull(exercises[row.exerciseUuid])
            val plan = plans[row.exerciseUuid].orEmpty()
            val completedPositions = setsByPerformed[row.uuid]
                .orEmpty()
                .mapTo(mutableSetOf(), SetEntity::position)
            val expectedPositions = buildSet {
                addAll(plan.indices)
                addAll(completedPositions)
            }.sorted()
            if (expectedPositions.isNotEmpty() && expectedPositions.all(completedPositions::contains)) {
                completedExercises++
                return@forEach
            }
            if (expectedPositions.isEmpty()) {
                return phoneAction(
                    databaseEpoch = databaseEpoch,
                    sessionUuid = session.uuid,
                    revision = session.wearRevision,
                    reason = PhoneActionReason.NoSetRows(
                        performedExerciseUuid = row.uuid.toWireUuid(),
                        exerciseName = BoundedDisplayName.from(exercise.name),
                    ),
                )
            }

            val targetPosition = requireNotNull(expectedPositions.firstOrNull { it !in completedPositions })
            val planSet = requireNotNull(plan.getOrNull(targetPosition)) {
                "An incomplete persisted target must originate from a plan position"
            }
            val numeric = snapshotNumeric(
                exerciseType = exercise.type,
                reps = planSet.reps,
                weight = planSet.weight,
            )
            if (numeric is SnapshotNumeric.Unsupported) {
                return phoneAction(
                    databaseEpoch = databaseEpoch,
                    sessionUuid = session.uuid,
                    revision = session.wearRevision,
                    reason = PhoneActionReason.UnsupportedNumericValues(
                        field = numeric.field,
                        performedExerciseUuid = row.uuid.toWireUuid(),
                        exerciseName = BoundedDisplayName.from(exercise.name),
                    ),
                )
            }
            numeric as SnapshotNumeric.Value
            return SnapshotData(
                databaseEpoch = databaseEpoch,
                payload = SnapshotPayload.ActiveWithTarget(
                    sessionUuid = session.uuid.toWireUuid(),
                    sessionRevision = session.wearRevision,
                    trainingName = BoundedDisplayName.from(training.name),
                    completedExercises = completedExercises,
                    totalExercises = performed.size,
                    target = ActiveTarget(
                        performedExerciseUuid = row.uuid.toWireUuid(),
                        exerciseName = BoundedDisplayName.from(exercise.name),
                        setPosition = targetPosition,
                        setOrdinal = expectedPositions.indexOf(targetPosition) + 1,
                        totalSets = expectedPositions.size,
                        reps = numeric.reps,
                        weightHundredthsKg = numeric.weightHundredthsKg,
                        exerciseType = exercise.type.toWire(),
                        setType = planSet.type.toWire(),
                    ),
                    mutationAuthority = MutationAuthority.Unavailable(
                        MutationUnavailableReason.FRESH_HANDSHAKE_REQUIRED,
                    ),
                ),
            )
        }

        return SnapshotData(
            databaseEpoch = databaseEpoch,
            payload = SnapshotPayload.WorkoutComplete(
                sessionUuid = session.uuid.toWireUuid(),
                sessionRevision = session.wearRevision,
                trainingName = BoundedDisplayName.from(training.name),
                completedExercises = completedExercises,
                totalExercises = performed.size,
            ),
        )
    }

    fun payloadTooLarge(base: SnapshotData): SnapshotData {
        val identity = base.payload.sessionIdentityOrNull() ?: return base
        return SnapshotData(
            databaseEpoch = base.databaseEpoch,
            payload = SnapshotPayload.PhoneActionRequired(
                sessionUuid = identity.first,
                sessionRevision = identity.second,
                reason = PhoneActionReason.PayloadTooLarge,
            ),
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

    private fun phoneAction(
        databaseEpoch: CanonicalUuid,
        sessionUuid: Uuid,
        revision: Long,
        reason: PhoneActionReason,
    ): SnapshotData = SnapshotData(
        databaseEpoch = databaseEpoch,
        payload = SnapshotPayload.PhoneActionRequired(
            sessionUuid = sessionUuid.toWireUuid(),
            sessionRevision = revision,
            reason = reason,
        ),
    )
}

internal sealed interface SnapshotNumeric {
    data class Value(val reps: Int, val weightHundredthsKg: Int?) : SnapshotNumeric
    data class Unsupported(val field: NumericField) : SnapshotNumeric
}

internal fun snapshotNumeric(
    exerciseType: ExerciseTypeEntity,
    reps: Int,
    weight: Double?,
): SnapshotNumeric {
    if (reps !in 0..WearProtocol.MAX_WEAR_REPS) {
        return SnapshotNumeric.Unsupported(NumericField.REPS)
    }
    if (exerciseType == ExerciseTypeEntity.WEIGHTLESS) {
        return SnapshotNumeric.Value(reps, weightHundredthsKg = null)
    }
    weight ?: return SnapshotNumeric.Value(reps, null)
    if (!weight.isFinite() || weight < 0.0 || weight.isNegativeZero()) {
        return SnapshotNumeric.Unsupported(NumericField.WEIGHT)
    }
    val hundredths = runCatching {
        BigDecimal.valueOf(weight).movePointRight(2).intValueExact()
    }.getOrNull() ?: return SnapshotNumeric.Unsupported(NumericField.WEIGHT)
    if (hundredths !in 0..WearProtocol.MAX_WEAR_WEIGHT_HUNDREDTHS_KG ||
        hundredths.toDouble() / HUNDREDTHS_PER_KG != weight
    ) {
        return SnapshotNumeric.Unsupported(NumericField.WEIGHT)
    }
    return SnapshotNumeric.Value(reps, hundredths)
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
    is SnapshotPayload.ActiveWithTarget -> sessionUuid to sessionRevision
    is SnapshotPayload.PhoneActionRequired -> sessionUuid to sessionRevision
    is SnapshotPayload.WorkoutComplete -> sessionUuid to sessionRevision
}

private const val NEGATIVE_ZERO: Double = -0.0
private const val HUNDREDTHS_PER_KG: Double = 100.0

private fun Double.isNegativeZero(): Boolean =
    java.lang.Double.doubleToRawLongBits(this) ==
        java.lang.Double.doubleToRawLongBits(NEGATIVE_ZERO)
