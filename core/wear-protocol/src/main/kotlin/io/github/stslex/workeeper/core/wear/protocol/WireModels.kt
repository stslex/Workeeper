// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.github.stslex.workeeper.core.wear.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("operation")
sealed interface WearEnvelope {
    val schemaVersion: Int
    val correlationId: CanonicalUuid
}

@Serializable
@SerialName("get_active_workout")
data class GetActiveWorkoutRequest(
    override val schemaVersion: Int,
    override val correlationId: CanonicalUuid,
) : WearEnvelope

@Serializable
@SerialName("active_workout_snapshot")
data class ActiveWorkoutSnapshotResponse(
    override val schemaVersion: Int,
    override val correlationId: CanonicalUuid,
    val snapshot: SnapshotData,
) : WearEnvelope

@Serializable
@SerialName("complete_current_set")
data class CompleteCurrentSetRequest(
    override val schemaVersion: Int,
    override val correlationId: CanonicalUuid,
    val commandId: CanonicalUuid,
    val databaseEpoch: CanonicalUuid,
    val sessionUuid: CanonicalUuid,
    val sessionRevision: Long,
    val mutationLeaseId: CanonicalUuid,
    val mutationLeaseGeneration: Long,
    val body: CompleteCurrentSetBody,
) : WearEnvelope

@Serializable
@SerialName("complete_current_set_response")
data class CompleteCurrentSetResponse(
    override val schemaVersion: Int,
    override val correlationId: CanonicalUuid,
    val commandId: CanonicalUuid,
    val outcome: CompleteCommandOutcome,
    val replacement: SnapshotData,
) : WearEnvelope

@Serializable
data class CompleteCurrentSetBody(
    val performedExerciseUuid: CanonicalUuid,
    val setPosition: Int,
    val reps: Int,
    val weightHundredthsKg: Int?,
    val exerciseType: ExerciseTypeWire,
    val setType: SetTypeWire,
)

@Serializable
data class SnapshotData(
    val databaseEpoch: CanonicalUuid,
    val payload: SnapshotPayload,
)

@Serializable
@JsonClassDiscriminator("state")
sealed interface SnapshotPayload {

    @Serializable
    @SerialName("no_session")
    data object NoSession : SnapshotPayload

    @Serializable
    @SerialName("active_with_target")
    data class ActiveWithTarget(
        val sessionUuid: CanonicalUuid,
        val sessionRevision: Long,
        val trainingName: BoundedDisplayName,
        val completedExercises: Int,
        val totalExercises: Int,
        val target: ActiveTarget,
        val mutationAuthority: MutationAuthority,
    ) : SnapshotPayload {

        init {
            require(sessionRevision >= 0L)
            require(completedExercises >= 0)
            require(totalExercises >= completedExercises)
        }
    }

    @Serializable
    @SerialName("phone_action_required")
    data class PhoneActionRequired(
        val sessionUuid: CanonicalUuid,
        val sessionRevision: Long,
        val reason: PhoneActionReason,
    ) : SnapshotPayload {

        init {
            require(sessionRevision >= 0L)
        }
    }

    @Serializable
    @SerialName("workout_complete")
    data class WorkoutComplete(
        val sessionUuid: CanonicalUuid,
        val sessionRevision: Long,
        val trainingName: BoundedDisplayName,
        val completedExercises: Int,
        val totalExercises: Int,
    ) : SnapshotPayload {

        init {
            require(sessionRevision >= 0L)
            require(completedExercises >= 0)
            require(totalExercises >= completedExercises)
        }
    }
}

@Serializable
data class ActiveTarget(
    val performedExerciseUuid: CanonicalUuid,
    val exerciseName: BoundedDisplayName,
    val setPosition: Int,
    val setOrdinal: Int,
    val totalSets: Int,
    val reps: Int,
    val weightHundredthsKg: Int?,
    val exerciseType: ExerciseTypeWire,
    val setType: SetTypeWire,
) {

    init {
        require(setPosition >= 0)
        require(setOrdinal >= 1)
        require(totalSets >= setOrdinal)
        require(reps in 0..WearProtocol.MAX_WEAR_REPS)
        require(weightHundredthsKg == null || weightHundredthsKg in 0..WearProtocol.MAX_WEAR_WEIGHT_HUNDREDTHS_KG)
        require(exerciseType != ExerciseTypeWire.WEIGHTLESS || weightHundredthsKg == null)
    }
}

@Serializable
@JsonClassDiscriminator("kind")
sealed interface MutationAuthority {

    @Serializable
    @SerialName("granted")
    data class Granted(
        val mutationLeaseId: CanonicalUuid,
        val mutationLeaseGeneration: Long,
        val leaseRemainingAtPhoneSendMs: Long,
    ) : MutationAuthority {

        init {
            require(mutationLeaseGeneration > 0L)
            require(leaseRemainingAtPhoneSendMs in 1L..WearProtocol.MAX_MUTATION_WINDOW_MS)
        }
    }

    @Serializable
    @SerialName("unavailable")
    data class Unavailable(
        val reason: MutationUnavailableReason,
    ) : MutationAuthority
}

@Serializable
enum class MutationUnavailableReason {
    @SerialName("fresh_handshake_required")
    FRESH_HANDSHAKE_REQUIRED,
}

@Serializable
@JsonClassDiscriminator("kind")
sealed interface PhoneActionReason {

    @Serializable
    @SerialName("no_set_rows")
    data class NoSetRows(
        val performedExerciseUuid: CanonicalUuid,
        val exerciseName: BoundedDisplayName,
    ) : PhoneActionReason

    @Serializable
    @SerialName("unsupported_numeric_values")
    data class UnsupportedNumericValues(
        val field: NumericField,
        val performedExerciseUuid: CanonicalUuid,
        val exerciseName: BoundedDisplayName,
    ) : PhoneActionReason

    @Serializable
    @SerialName("payload_too_large")
    data object PayloadTooLarge : PhoneActionReason
}

@Serializable
@Suppress("MagicNumber") // Normative FingerprintV1 enum bytes.
enum class ExerciseTypeWire(val fingerprintByte: Byte) {
    @SerialName("weighted")
    WEIGHTED(0x01),

    @SerialName("weightless")
    WEIGHTLESS(0x02),
}

@Serializable
@Suppress("MagicNumber") // Normative FingerprintV1 enum bytes.
enum class SetTypeWire(val fingerprintByte: Byte) {
    @SerialName("warm")
    WARM(0x01),

    @SerialName("work")
    WORK(0x02),

    @SerialName("fail")
    FAIL(0x03),

    @SerialName("drop")
    DROP(0x04),
}

@Serializable
enum class NumericField {
    @SerialName("reps")
    REPS,

    @SerialName("weight")
    WEIGHT,
}

@Serializable
enum class InvalidValueReason {
    @SerialName("below_minimum")
    BELOW_MINIMUM,

    @SerialName("above_maximum")
    ABOVE_MAXIMUM,

    @SerialName("must_be_null_for_weightless")
    MUST_BE_NULL_FOR_WEIGHTLESS,
}

@Serializable
enum class ImmutableTypeField {
    @SerialName("exercise_type")
    EXERCISE_TYPE,

    @SerialName("set_type")
    SET_TYPE,
}

@Serializable
enum class ProtocolRejectionReason {
    @SerialName("invalid_numeric_encoding")
    INVALID_NUMERIC_ENCODING,

    @SerialName("unsupported_fingerprint_version")
    UNSUPPORTED_FINGERPRINT_VERSION,

    @SerialName("command_fingerprint_mismatch")
    COMMAND_FINGERPRINT_MISMATCH,
}

@Serializable
@JsonClassDiscriminator("kind")
sealed interface CompleteCommandOutcome {

    @Serializable
    @SerialName("applied")
    data object Applied : CompleteCommandOutcome

    @Serializable
    @SerialName("already_applied")
    data object AlreadyApplied : CompleteCommandOutcome

    @Serializable
    @SerialName("authorization_expired")
    data object AuthorizationExpired : CompleteCommandOutcome

    @Serializable
    @SerialName("stale_revision")
    data object StaleRevision : CompleteCommandOutcome

    @Serializable
    @SerialName("target_changed")
    data object TargetChanged : CompleteCommandOutcome

    @Serializable
    @SerialName("no_active_session")
    data object NoActiveSession : CompleteCommandOutcome

    @Serializable
    @SerialName("invalid_values")
    data class InvalidValues(
        val field: NumericField,
        val reason: InvalidValueReason,
    ) : CompleteCommandOutcome

    @Serializable
    @SerialName("immutable_type_mismatch")
    data class ImmutableTypeMismatch(
        val field: ImmutableTypeField,
    ) : CompleteCommandOutcome

    @Serializable
    @SerialName("retryable_temporary_failure")
    data object RetryableTemporaryFailure : CompleteCommandOutcome

    @Serializable
    @SerialName("protocol_rejected")
    data class ProtocolRejected(
        val reason: ProtocolRejectionReason,
    ) : CompleteCommandOutcome
}
