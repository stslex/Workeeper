// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

/** wear-phase-2-exercise-list.md §5.5: every outcome against every payload kind and authority. */
class ProtocolPairingValidatorTest {

    private val noSession = SnapshotPayload.NoSession
    private val tooLarge = SnapshotPayload.PayloadTooLarge(ProtocolFixtures.sessionUuid, 11)
    private val granted = ProtocolFixtures.activePayload(ProtocolFixtures.granted)
    private val unavailable = ProtocolFixtures.activePayload(ProtocolFixtures.unavailable)
    private val payloads = listOf(noSession, tooLarge, granted, unavailable)

    @Test
    fun `the pairing matrix allows exactly the replacements of section 5_5`() {
        val outcomes: Map<CommandOutcome, Set<SnapshotPayload>> = mapOf(
            CommandOutcome.NoActiveSession to setOf(noSession),
            CommandOutcome.TargetChanged to setOf(tooLarge, granted, unavailable),
            CommandOutcome.InvalidValues(NumericField.REPS, InvalidValueReason.BELOW_MINIMUM) to setOf(unavailable),
            CommandOutcome.ImmutableTypeMismatch(ImmutableTypeField.SET_TYPE) to setOf(unavailable),
            CommandOutcome.ProtocolRejected(ProtocolRejectionReason.COMMAND_FINGERPRINT_MISMATCH) to
                setOf(noSession, tooLarge, unavailable),
            CommandOutcome.RetryableTemporaryFailure to setOf(granted),
            CommandOutcome.Applied to payloads.toSet(),
            CommandOutcome.AlreadyApplied to payloads.toSet(),
            CommandOutcome.AuthorizationExpired to payloads.toSet(),
            CommandOutcome.StaleRevision to payloads.toSet(),
        )
        assertEquals(OUTCOME_VARIANTS, outcomes.size, "every outcome variant is in the matrix")
        outcomes.forEach { (outcome, allowed) ->
            payloads.forEach { payload ->
                assertEquals(
                    payload in allowed,
                    ProtocolPairingValidator.isValid(outcome, payload),
                    "$outcome with ${payload.describe()}",
                )
            }
        }
    }

    private fun SnapshotPayload.describe(): String =
        listOfNotNull(this::class.simpleName, (this as? SnapshotPayload.Active)?.mutationAuthority).joinToString(" ")

    private companion object {
        /** `CommandOutcome`'s variants: v1's ten, renamed (§5.2). */
        const val OUTCOME_VARIANTS = 10
    }
}
