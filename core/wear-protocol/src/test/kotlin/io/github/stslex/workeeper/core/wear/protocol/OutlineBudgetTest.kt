// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * wear-phase-2-exercise-list.md §5.7 (V5, P15) on the real codec: an outline is sent only while its
 * widest snapshot response fits the budget, and then its largest command response fits the envelope.
 * Each test prints its sizes, the measurement of §14 Q6.
 */
class OutlineBudgetTest {

    @Test
    fun `the mockup's typical session is sent`() {
        val snapshot = snapshot(typical())
        report("typical 6 exercises 19 sets", snapshot)
        assertTrue(OutlineBudget.fits(snapshot))
        assertTrue(largestCommandResponseSize(snapshot) <= WearProtocol.MAX_ENVELOPE_BYTES)
    }

    @Test
    fun `the 20 x 5 stress outline is sent and its largest command response fits`() {
        val snapshot = snapshot(stress(exercises = 20, sets = 5))
        report("stress 20 x 5", snapshot)
        assertTrue(OutlineBudget.fits(snapshot))
        assertTrue(largestCommandResponseSize(snapshot) <= WearProtocol.MAX_ENVELOPE_BYTES)
    }

    @Test
    fun `an outline within 64 bytes under the budget is sent and one just over falls back`() {
        val under = outlineOfSize(WearProtocol.OUTLINE_BUDGET_BYTES - 32)
        val atBudget = outlineOfSize(WearProtocol.OUTLINE_BUDGET_BYTES)
        val over = outlineOfSize(WearProtocol.OUTLINE_BUDGET_BYTES + 1)
        report("budget - 32", under)
        report("budget + 1", over)

        assertTrue(OutlineBudget.fits(under))
        assertTrue(largestCommandResponseSize(under) <= WearProtocol.MAX_ENVELOPE_BYTES)
        assertTrue(OutlineBudget.fits(atBudget))
        assertTrue(largestCommandResponseSize(atBudget) <= WearProtocol.MAX_ENVELOPE_BYTES)
        assertFalse(OutlineBudget.fits(over))
        // §10.1: the outline that fits is sent as it is; the one just over becomes PayloadTooLarge of its session.
        assertEquals(under, OutlineBudget.bounded(under))
        assertEquals(over.copy(payload = over.fallback()), OutlineBudget.bounded(over))
    }

    @Test
    fun `every outline the budget keeps has a largest command response inside the envelope`() {
        listOf(
            WearProtocol.OUTLINE_BUDGET_BYTES - 64,
            WearProtocol.OUTLINE_BUDGET_BYTES,
            WearProtocol.OUTLINE_BUDGET_BYTES + 1,
            WearProtocol.MAX_ENVELOPE_BYTES - WearProtocol.COMMAND_HEADROOM_BYTES / 2,
            WearProtocol.MAX_ENVELOPE_BYTES - 1,
            WearProtocol.MAX_ENVELOPE_BYTES,
        ).forEach { target ->
            val snapshot = outlineOfSize(target)
            if (OutlineBudget.fits(snapshot)) {
                val command = largestCommandResponseSize(snapshot)
                assertTrue(command <= WearProtocol.MAX_ENVELOPE_BYTES, "snapshot $target B kept, command $command B")
            }
        }
    }

    @Test
    fun `larger sessions fall back and the fallback stays small`() {
        listOf(
            "stress 20 x 6" to stress(exercises = 20, sets = 6),
            "stress 25 x 6" to stress(exercises = 25, sets = 6),
            "stress 30 x 5" to stress(exercises = 30, sets = 5),
        ).forEach { (name, exercises) ->
            val snapshot = snapshot(exercises)
            report(name, snapshot)
            assertFalse(OutlineBudget.fits(snapshot), name)
            assertEquals(snapshot.copy(payload = snapshot.fallback()), OutlineBudget.bounded(snapshot), name)
        }
        val fallback = WearProtocolCodec.encode(
            ActiveWorkoutSnapshotResponse(
                WearProtocol.SCHEMA_VERSION,
                WIDEST_UUID,
                SnapshotData(WIDEST_UUID, SnapshotPayload.PayloadTooLarge(WIDEST_UUID, Long.MAX_VALUE)),
            ),
        )
        println("Q6 PayloadTooLarge fallback: ${fallback.size} B")
        assertTrue(fallback.size < WearProtocol.MAX_PAYLOAD_TOO_LARGE_FALLBACK_BYTES)
    }

    @Test
    fun `the 12 x 4 and the 14 x 4 sessions with 512-byte names are sent`() {
        listOf(
            "stress 12 x 4" to stress(exercises = 12, sets = 4),
            "14 x 4 with 512-byte names" to stress(exercises = 14, sets = 4, name = "Ж".repeat(256)),
        ).forEach { (name, exercises) ->
            val snapshot = snapshot(exercises, trainingName = if ("512" in name) "Ж".repeat(256) else STRESS_NAME)
            report(name, snapshot)
            assertTrue(OutlineBudget.fits(snapshot), name)
            assertTrue(largestCommandResponseSize(snapshot) <= WearProtocol.MAX_ENVELOPE_BYTES, name)
        }
    }

    @Test
    fun `the budget check gives the same answer for any lease`() {
        listOf(
            outlineOfSize(WearProtocol.OUTLINE_BUDGET_BYTES),
            outlineOfSize(WearProtocol.OUTLINE_BUDGET_BYTES + 1),
        ).forEach { snapshot ->
            val active = snapshot.payload as SnapshotPayload.Active
            val answers = listOf(
                ProtocolFixtures.unavailable,
                MutationAuthority.Granted(ProtocolFixtures.leaseId, 1, 1),
                MutationAuthority.Granted(WIDEST_UUID, Long.MAX_VALUE, WearProtocol.MAX_MUTATION_WINDOW_MS),
            ).map { authority ->
                OutlineBudget.fits(snapshot.copy(payload = active.copy(mutationAuthority = authority)))
            }
            assertEquals(1, answers.toSet().size, "$answers")
        }
    }

    /** The snapshot response with the widest granted authority, as the phone measures it. */
    private fun snapshotResponseSize(snapshot: SnapshotData): Int = WearProtocolCodec.serialize(
        ActiveWorkoutSnapshotResponse(WearProtocol.SCHEMA_VERSION, WIDEST_UUID, snapshot.widest()),
    ).size

    /** The widest outcome with the widest granted authority: no real response is larger. */
    private fun largestCommandResponseSize(snapshot: SnapshotData): Int = OUTCOMES.maxOf { outcome ->
        WearProtocolCodec.serialize(
            WatchCommandResponse(WearProtocol.SCHEMA_VERSION, WIDEST_UUID, WIDEST_UUID, outcome, snapshot.widest()),
        ).size
    }

    private fun report(name: String, snapshot: SnapshotData) {
        println(
            "Q6 $name: snapshot response ${snapshotResponseSize(snapshot)} B, largest command response " +
                "${largestCommandResponseSize(snapshot)} B, budget ${WearProtocol.OUTLINE_BUDGET_BYTES} B, " +
                "sent ${OutlineBudget.fits(snapshot)}",
        )
    }

    private fun SnapshotData.widest(): SnapshotData =
        copy(payload = (payload as SnapshotPayload.Active).copy(mutationAuthority = WIDEST_AUTHORITY))

    /** What §5.7 sends in place of this outline: `PayloadTooLarge` of its session at its revision. */
    private fun SnapshotData.fallback(): SnapshotPayload.PayloadTooLarge {
        val active = payload as SnapshotPayload.Active
        return SnapshotPayload.PayloadTooLarge(active.sessionUuid, active.sessionRevision)
    }

    /**
     * The 20 x 5 stress outline with ASCII exercise names resized until its widest snapshot response
     * is [target] bytes: names are 1 byte per character, so the size moves by exactly one per character.
     */
    private fun outlineOfSize(target: Int): SnapshotData {
        val exercises = stress(exercises = 20, sets = 5).toMutableList()
        var index = 0
        var snapshot = snapshot(exercises)
        while (snapshotResponseSize(snapshot) != target) {
            val delta = target - snapshotResponseSize(snapshot)
            val name = (exercises[index].name as BoundedDisplayName.Value).value
            val length = (name.toByteArray().size + delta).coerceIn(1, WearProtocol.MAX_DISPLAY_NAME_UTF8_BYTES)
            exercises[index] = exercises[index].copy(name = BoundedDisplayName.Value("x".repeat(length)))
            snapshot = snapshot(exercises)
            index += 1
        }
        return snapshot
    }

    private fun snapshot(exercises: List<OutlineExercise>, trainingName: String = STRESS_NAME) = SnapshotData(
        databaseEpoch = WIDEST_UUID,
        payload = SnapshotPayload.Active(
            sessionUuid = WIDEST_UUID,
            sessionRevision = Long.MAX_VALUE,
            trainingName = BoundedDisplayName.Value(trainingName),
            exercises = exercises,
            mutationAuthority = WIDEST_AUTHORITY,
        ),
    )

    /** [exercises] exercises of [sets] open sets each, every value at the protocol maximum. */
    private fun stress(exercises: Int, sets: Int, name: String = STRESS_NAME): List<OutlineExercise> =
        List(exercises) { index ->
            OutlineExercise(
                performedExerciseUuid = exerciseId(index),
                name = BoundedDisplayName.Value(name),
                exerciseType = ExerciseTypeWire.WEIGHTED,
                skipped = false,
                sets = List(sets) { position ->
                    OutlineSet.Open(
                        position = position,
                        setType = SetTypeWire.WORK,
                        reps = WearProtocol.MAX_WEAR_REPS,
                        weightHundredthsKg = WearProtocol.MAX_WEAR_WEIGHT_HUNDREDTHS_KG,
                    )
                },
            )
        }

    /** The approved mockup's typical fixture: six exercises, 19 sets, Russian names, one skipped. */
    private fun typical(): List<OutlineExercise> {
        fun done(position: Int, weight: Int?, reps: Int, type: SetTypeWire = SetTypeWire.WORK) =
            OutlineSet.Done(position, type, reps, weight)
        fun open(position: Int, weight: Int?, reps: Int, type: SetTypeWire = SetTypeWire.WORK) =
            OutlineSet.Open(position, type, reps, weight)
        fun exercise(index: Int, name: String, weighted: Boolean, sets: List<OutlineSet>, skipped: Boolean = false) =
            OutlineExercise(
                performedExerciseUuid = exerciseId(index),
                name = BoundedDisplayName.Value(name),
                exerciseType = if (weighted) ExerciseTypeWire.WEIGHTED else ExerciseTypeWire.WEIGHTLESS,
                skipped = skipped,
                sets = sets,
            )
        return listOf(
            exercise(
                0,
                "Жим штанги лёжа",
                weighted = true,
                sets = listOf(
                    done(0, 4_000, 12, SetTypeWire.WARM),
                    done(1, 6_000, 8),
                    done(2, 7_000, 6),
                    done(3, 7_000, 6),
                ),
            ),
            exercise(
                1,
                "Жим гантелей на наклонной скамье",
                weighted = true,
                sets = listOf(done(0, 2_400, 10), open(1, 2_400, 10), open(2, 2_400, 8)),
            ),
            exercise(
                2,
                "Отжимания на брусьях",
                weighted = false,
                sets = listOf(open(0, null, 12), open(1, null, 10), open(2, null, 10)),
            ),
            exercise(
                3,
                "Сведение рук в кроссовере",
                weighted = true,
                sets = listOf(open(0, 1_500, 15), open(1, 1_500, 15), open(2, 1_500, 12)),
            ),
            exercise(
                4,
                "Французский жим лёжа",
                weighted = true,
                skipped = true,
                sets = listOf(open(0, 2_000, 12), open(1, 2_000, 12), open(2, 2_000, 10)),
            ),
            exercise(
                5,
                "Разгибание рук на блоке",
                weighted = true,
                sets = listOf(open(0, 2_500, 12), open(1, 2_500, 12), open(2, 2_250, 15, SetTypeWire.DROP)),
            ),
        )
    }

    private fun exerciseId(index: Int) =
        ProtocolFixtures.uuid("cccccccc-0000-4000-8000-${index.toString().padStart(12, '0')}")

    private companion object {
        /** 61 Cyrillic characters, 122 UTF-8 bytes. */
        val STRESS_NAME = "Ж".repeat(61)
        val WIDEST_UUID = ProtocolFixtures.uuid("ffffffff-ffff-4fff-bfff-ffffffffffff")
        val WIDEST_AUTHORITY =
            MutationAuthority.Granted(WIDEST_UUID, Long.MAX_VALUE, WearProtocol.MAX_MUTATION_WINDOW_MS)
        val OUTCOMES: List<CommandOutcome> = listOf(
            CommandOutcome.Applied,
            CommandOutcome.AlreadyApplied,
            CommandOutcome.AuthorizationExpired,
            CommandOutcome.StaleRevision,
            CommandOutcome.TargetChanged,
            CommandOutcome.NoActiveSession,
            CommandOutcome.RetryableTemporaryFailure,
        ) + NumericField.entries.flatMap { field ->
            InvalidValueReason.entries.map { reason -> CommandOutcome.InvalidValues(field, reason) }
        } + ImmutableTypeField.entries.map(CommandOutcome::ImmutableTypeMismatch) +
            ProtocolRejectionReason.entries.map(CommandOutcome::ProtocolRejected)
    }
}
