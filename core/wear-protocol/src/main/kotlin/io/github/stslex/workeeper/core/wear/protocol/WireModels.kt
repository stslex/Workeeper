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
@SerialName("watch_command")
data class WatchCommandRequest(
    override val schemaVersion: Int,
    override val correlationId: CanonicalUuid,
    val commandId: CanonicalUuid,
    val databaseEpoch: CanonicalUuid,
    val sessionUuid: CanonicalUuid,
    val sessionRevision: Long,
    val mutationLeaseId: CanonicalUuid,
    val mutationLeaseGeneration: Long,
    val body: WatchCommandBody,
) : WearEnvelope

@Serializable
@SerialName("watch_command_response")
data class WatchCommandResponse(
    override val schemaVersion: Int,
    override val correlationId: CanonicalUuid,
    val commandId: CanonicalUuid,
    val outcome: CommandOutcome,
    val replacement: SnapshotData,
) : WearEnvelope

/** What a watch command asks of one performed exercise (wear-phase-2-exercise-list.md §5.2, P1). */
@Serializable
@JsonClassDiscriminator("kind")
sealed interface WatchCommandBody {
    val performedExerciseUuid: CanonicalUuid

    @Serializable
    @SerialName("complete_set")
    data class CompleteSet(
        override val performedExerciseUuid: CanonicalUuid,
        val setPosition: Int,
        val reps: Int,
        val weightHundredthsKg: Int?,
        val exerciseType: ExerciseTypeWire,
        val setType: SetTypeWire,
    ) : WatchCommandBody

    /** [skipped] true skips the exercise; false returns it to the session. */
    @Serializable
    @SerialName("set_exercise_skipped")
    data class SetExerciseSkipped(
        override val performedExerciseUuid: CanonicalUuid,
        val skipped: Boolean,
    ) : WatchCommandBody
}

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

    /** The session outline: every performed exercise in phone order, skipped ones included (V2). */
    @Serializable
    @SerialName("active")
    data class Active(
        val sessionUuid: CanonicalUuid,
        val sessionRevision: Long,
        val trainingName: BoundedDisplayName,
        val exercises: List<OutlineExercise>,
        val mutationAuthority: MutationAuthority,
    ) : SnapshotPayload {

        init {
            require(sessionRevision >= 0L)
            require(exercises.map(OutlineExercise::performedExerciseUuid).toSet().size == exercises.size)
        }
    }

    @Serializable
    @SerialName("payload_too_large")
    data class PayloadTooLarge(
        val sessionUuid: CanonicalUuid,
        val sessionRevision: Long,
    ) : SnapshotPayload {

        init {
            require(sessionRevision >= 0L)
        }
    }
}

@Serializable
data class OutlineExercise(
    val performedExerciseUuid: CanonicalUuid,
    val name: BoundedDisplayName,
    val exerciseType: ExerciseTypeWire,
    val skipped: Boolean,
    val sets: List<OutlineSet>,
) {

    init {
        require(sets.zipWithNext().all { (left, right) -> left.position < right.position })
        require(exerciseType != ExerciseTypeWire.WEIGHTLESS || sets.none { it.weightHundredthsKgOrNull() != null })
    }
}

@Serializable
@JsonClassDiscriminator("kind")
sealed interface OutlineSet {
    val position: Int
    val setType: SetTypeWire

    /** Not done; its planned values are inside the watch domain, so the watch may complete it. */
    @Serializable
    @SerialName("open")
    data class Open(
        override val position: Int,
        override val setType: SetTypeWire,
        val reps: Int,
        val weightHundredthsKg: Int?,
    ) : OutlineSet {

        init {
            require(position >= 0)
            require(reps in 0..WearProtocol.MAX_WEAR_REPS)
            require(weightHundredthsKg == null || weightHundredthsKg in 0..WearProtocol.MAX_WEAR_WEIGHT_HUNDREDTHS_KG)
        }
    }

    /** Done. Values are display-only; a value outside the watch domain is null. */
    @Serializable
    @SerialName("done")
    data class Done(
        override val position: Int,
        override val setType: SetTypeWire,
        val reps: Int?,
        val weightHundredthsKg: Int?,
    ) : OutlineSet {

        init {
            require(position >= 0)
            require(reps == null || reps in 0..WearProtocol.MAX_WEAR_REPS)
            require(weightHundredthsKg == null || weightHundredthsKg in 0..WearProtocol.MAX_WEAR_WEIGHT_HUNDREDTHS_KG)
        }
    }

    /** Not done, and a planned value is outside the watch domain (P8): shown, not completable here. */
    @Serializable
    @SerialName("blocked")
    data class Blocked(
        override val position: Int,
        override val setType: SetTypeWire,
        val field: NumericField,
    ) : OutlineSet {

        init {
            require(position >= 0)
        }
    }
}

private fun OutlineSet.weightHundredthsKgOrNull(): Int? = when (this) {
    is OutlineSet.Open -> weightHundredthsKg
    is OutlineSet.Done -> weightHundredthsKg
    is OutlineSet.Blocked -> null
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
@Suppress("MagicNumber") // Normative FingerprintV2 enum bytes.
enum class ExerciseTypeWire(val fingerprintByte: Byte) {
    @SerialName("weighted")
    WEIGHTED(0x01),

    @SerialName("weightless")
    WEIGHTLESS(0x02),
}

@Serializable
@Suppress("MagicNumber") // Normative FingerprintV2 enum bytes.
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
sealed interface CommandOutcome {

    @Serializable
    @SerialName("applied")
    data object Applied : CommandOutcome

    @Serializable
    @SerialName("already_applied")
    data object AlreadyApplied : CommandOutcome

    @Serializable
    @SerialName("authorization_expired")
    data object AuthorizationExpired : CommandOutcome

    @Serializable
    @SerialName("stale_revision")
    data object StaleRevision : CommandOutcome

    @Serializable
    @SerialName("target_changed")
    data object TargetChanged : CommandOutcome

    @Serializable
    @SerialName("no_active_session")
    data object NoActiveSession : CommandOutcome

    @Serializable
    @SerialName("invalid_values")
    data class InvalidValues(
        val field: NumericField,
        val reason: InvalidValueReason,
    ) : CommandOutcome

    @Serializable
    @SerialName("immutable_type_mismatch")
    data class ImmutableTypeMismatch(
        val field: ImmutableTypeField,
    ) : CommandOutcome

    @Serializable
    @SerialName("retryable_temporary_failure")
    data object RetryableTemporaryFailure : CommandOutcome

    @Serializable
    @SerialName("protocol_rejected")
    data class ProtocolRejected(
        val reason: ProtocolRejectionReason,
    ) : CommandOutcome
}
