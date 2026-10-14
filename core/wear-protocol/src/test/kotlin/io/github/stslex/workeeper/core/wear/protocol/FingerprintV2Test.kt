// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * wear-phase-2-exercise-list.md §5.6. The known-answer vectors were checked by an independent
 * computation of the same preimages (the PR's script), which first reproduced FingerprintV1's four.
 */
class FingerprintV2Test {

    @Test
    fun `complete_set known-answer vectors retain full stable and attempt digests`() {
        assertVector(weighted(), STABLE, COMPLETE_WEIGHTED_STABLE_PREIMAGE, COMPLETE_WEIGHTED_STABLE_FINGERPRINT)
        assertVector(weighted(), ATTEMPT, COMPLETE_WEIGHTED_ATTEMPT_PREIMAGE, COMPLETE_WEIGHTED_ATTEMPT_FINGERPRINT)
        assertVector(weightless(), STABLE, COMPLETE_WEIGHTLESS_STABLE_PREIMAGE, COMPLETE_WEIGHTLESS_STABLE_FINGERPRINT)
        assertVector(
            weightless(),
            ATTEMPT,
            COMPLETE_WEIGHTLESS_ATTEMPT_PREIMAGE,
            COMPLETE_WEIGHTLESS_ATTEMPT_FINGERPRINT,
        )
    }

    @Test
    fun `set_exercise_skipped known-answer vectors cover skip and return`() {
        assertVector(skip(), STABLE, SKIP_STABLE_PREIMAGE, SKIP_STABLE_FINGERPRINT)
        assertVector(skip(), ATTEMPT, SKIP_ATTEMPT_PREIMAGE, SKIP_ATTEMPT_FINGERPRINT)
        assertVector(giveBack(), STABLE, RETURN_STABLE_PREIMAGE, RETURN_STABLE_FINGERPRINT)
        assertVector(giveBack(), ATTEMPT, RETURN_ATTEMPT_PREIMAGE, RETURN_ATTEMPT_FINGERPRINT)
    }

    @Test
    fun `the preimage header names magic version purpose kind and field count`() {
        val headers = mapOf(
            pre(weighted(), STABLE) to "574b574600020101000c",
            pre(weighted(), ATTEMPT) to "574b574600020201000e",
            pre(skip(), STABLE) to "574b5746000201020008",
            pre(skip(), ATTEMPT) to "574b574600020202000a",
        )
        headers.forEach { (preimage, header) ->
            assertContentEquals(hex(header), preimage.copyOf(HEADER_BYTES))
        }
    }

    @Test
    fun `complete_set and set_exercise_skipped with equal common fields never share a digest`() {
        val common = weighted()
        listOf(true, false).forEach { skipped ->
            val other = common.withSkipped(skipped)
            FingerprintPurpose.entries.forEach { purpose ->
                assertNotEquals(fp(common, purpose), fp(other, purpose))
            }
        }
        FingerprintPurpose.entries.forEach { purpose ->
            assertNotEquals(fp(skip(), purpose), fp(skip().flipped(), purpose))
        }
    }

    @Test
    fun `UUID spelling is not fingerprint input and every canonical field changes its result`() {
        val base = weighted()
        val upperCaseUuid = base.copy(
            commandId = CanonicalUuid.parse("00112233-4455-6677-8899-AABBCCDDEEFF"),
        )
        assertContentEquals(
            fp(base, STABLE).encoded,
            fp(upperCaseUuid, STABLE).encoded,
        )
        val body = base.body as WatchCommandBody.CompleteSet
        val mutations = listOf(
            base.copy(sourceNodeId = "node-B"),
            base.copy(schemaVersion = 3),
            base.copy(commandId = ProtocolFixtures.uuid("00112233-4455-6677-8899-aabbccddee00")),
            base.copy(databaseEpoch = ProtocolFixtures.uuid("10213243-5465-7687-98a9-bacbdcedfe00")),
            base.copy(sessionUuid = ProtocolFixtures.uuid("11223344-5566-7788-99aa-bbccddeeff01")),
            base.copy(sessionRevision = 43),
            base.copy(body = body.copy(performedExerciseUuid = OTHER_EXERCISE)),
            base.copy(body = body.copy(setPosition = 4)),
            base.copy(body = body.copy(reps = 13)),
            base.copy(body = body.copy(weightHundredthsKg = null)),
            base.copy(body = body.copy(weightHundredthsKg = 0)),
            base.copy(body = body.copy(exerciseType = ExerciseTypeWire.WEIGHTLESS, weightHundredthsKg = null)),
            base.copy(body = body.copy(setType = SetTypeWire.DROP)),
        )
        val baseline = fp(base, STABLE)
        mutations.forEach { mutation ->
            assertNotEquals(baseline, fp(mutation, STABLE))
        }
        assertNotEquals(baseline, fp(base, ATTEMPT))
    }

    @Test
    fun `attempt fingerprint includes both lease fields while stable intent excludes them`() {
        listOf(weighted(), skip()).forEach { base ->
            val changedLease = base.copy(
                mutationLeaseId = ProtocolFixtures.uuid("abcdef01-2345-6789-abcd-ef0123456700"),
                mutationLeaseGeneration = 10,
            )
            assertContentEquals(
                fp(base, STABLE).encoded,
                fp(changedLease, STABLE).encoded,
            )
            assertNotEquals(
                fp(base, ATTEMPT),
                fp(changedLease, ATTEMPT),
            )
        }
    }

    @Test
    fun `strict preimage parser checks the header the per-kind tags and sizes and trailing bytes`() {
        val complete = pre(weighted(), STABLE)
        assertTrue(canonical(complete, STABLE, COMPLETE))
        val skipped = pre(skip(), ATTEMPT)
        assertTrue(canonical(skipped, ATTEMPT, SKIPPED))

        val malformedComplete = listOf(
            complete.copyOf().also { it[5] = 0x01 },
            complete.copyOf().also { it[6] = 0x02 },
            complete.copyOf().also { it[7] = 0x02 },
            complete.copyOf().also { it[9] = 0x0d },
            complete.copyOf().also { it[10] = 0x02 },
            complete.copyOf().also { it[14] = 0x07 },
            complete.copyOf().also { bytes -> bytes[indexOfTagValue(bytes, tag = 0x0b)] = 0x00 },
            complete.copyOf(complete.size - 1),
            complete + 0x00.toByte(),
        )
        malformedComplete.forEach { malformed ->
            assertFalse(canonical(malformed, STABLE, COMPLETE))
        }
        assertFalse(canonical(complete, STABLE, SKIPPED))
        val malformedSkip = listOf(
            skipped.copyOf().also { bytes -> bytes[indexOfTagValue(bytes, tag = 0x08)] = 0x02 },
            skipped.copyOf().also { it[7] = 0x01 },
            skipped.copyOf(skipped.size - 1),
            skipped + 0x00.toByte(),
        )
        malformedSkip.forEach { malformed ->
            assertFalse(canonical(malformed, ATTEMPT, SKIPPED))
        }
    }

    @Test
    fun `stored value rejects truncated digest and every version but 2`() {
        assertFailsWith<IllegalArgumentException> {
            FingerprintValue.parse(ByteArray(FingerprintValue.ENCODED_SIZE - 1))
        }
        listOf(0x01, 0x03).forEach { version ->
            assertFailsWith<UnsupportedFingerprintVersionException> {
                FingerprintValue.parse(ByteArray(FingerprintValue.ENCODED_SIZE).also { it[1] = version.toByte() })
            }
        }
        assertTrue(
            FingerprintValue.parse(hex(COMPLETE_WEIGHTED_STABLE_FINGERPRINT))
                .constantTimeEquals(fp(weighted(), STABLE)),
        )
    }

    private fun assertVector(
        command: FingerprintCommand,
        purpose: FingerprintPurpose,
        preimage: String,
        fingerprint: String,
    ) {
        val actualPreimage = pre(command, purpose)
        assertContentEquals(hex(preimage), actualPreimage)
        assertTrue(canonical(actualPreimage, purpose, command.kind))
        assertContentEquals(hex(fingerprint), fp(command, purpose).encoded)
        assertTrue(FingerprintValue.parse(hex(fingerprint)).constantTimeEquals(fp(command, purpose)))
    }

    private fun indexOfTagValue(bytes: ByteArray, tag: Int): Int {
        var index = HEADER_BYTES
        while (index < bytes.size) {
            val currentTag = bytes[index].toInt() and 0xff
            val length = ((bytes[index + 1].toInt() and 0xff) shl 24) or
                ((bytes[index + 2].toInt() and 0xff) shl 16) or
                ((bytes[index + 3].toInt() and 0xff) shl 8) or
                (bytes[index + 4].toInt() and 0xff)
            if (currentTag == tag) return index + 5
            index += 5 + length
        }
        error("tag $tag absent")
    }

    /** FingerprintV1Test's weighted command, at schema version 2. */
    private fun weighted() = FingerprintCommand(
        sourceNodeId = "node-A",
        schemaVersion = 2,
        commandId = ProtocolFixtures.uuid("00112233-4455-6677-8899-aabbccddeeff"),
        databaseEpoch = ProtocolFixtures.uuid("10213243-5465-7687-98a9-bacbdcedfe0f"),
        sessionUuid = ProtocolFixtures.uuid("11223344-5566-7788-99aa-bbccddeeff00"),
        sessionRevision = 42,
        body = WatchCommandBody.CompleteSet(
            performedExerciseUuid = ProtocolFixtures.uuid("ffeeddcc-bbaa-9988-7766-554433221100"),
            setPosition = 3,
            reps = 12,
            weightHundredthsKg = 7_250,
            exerciseType = ExerciseTypeWire.WEIGHTED,
            setType = SetTypeWire.WORK,
        ),
        mutationLeaseId = ProtocolFixtures.uuid("abcdef01-2345-6789-abcd-ef0123456789"),
        mutationLeaseGeneration = 9,
    )

    /** FingerprintV1Test's weightless command, at schema version 2. */
    private fun weightless() = FingerprintCommand(
        sourceNodeId = "watch-β",
        schemaVersion = 2,
        commandId = ProtocolFixtures.uuid("fedcba98-7654-3210-fedc-ba9876543210"),
        databaseEpoch = ProtocolFixtures.uuid("01020304-0506-0708-090a-0b0c0d0e0f10"),
        sessionUuid = ProtocolFixtures.uuid("11111111-2222-3333-8444-555555555555"),
        sessionRevision = 922_337_203_685_477,
        body = WatchCommandBody.CompleteSet(
            performedExerciseUuid = ProtocolFixtures.uuid("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"),
            setPosition = 0,
            reps = 1,
            weightHundredthsKg = null,
            exerciseType = ExerciseTypeWire.WEIGHTLESS,
            setType = SetTypeWire.WARM,
        ),
        mutationLeaseId = ProtocolFixtures.uuid("99999999-8888-4777-8666-555555555555"),
        mutationLeaseGeneration = 77,
    )

    /** Tags 01 to 07 of the weighted command, skipping its exercise. */
    private fun skip() = weighted().withSkipped(true)

    /** Tags 01 to 07 of the weightless command, returning its exercise. */
    private fun giveBack() = weightless().withSkipped(false)

    private fun FingerprintCommand.withSkipped(skipped: Boolean) =
        copy(body = WatchCommandBody.SetExerciseSkipped(body.performedExerciseUuid, skipped))

    private fun FingerprintCommand.flipped() =
        withSkipped(!(body as WatchCommandBody.SetExerciseSkipped).skipped)

    private fun pre(command: FingerprintCommand, purpose: FingerprintPurpose) = FingerprintV2.preimage(command, purpose)

    private fun fp(command: FingerprintCommand, purpose: FingerprintPurpose) =
        FingerprintV2.fingerprint(command, purpose)

    private fun canonical(bytes: ByteArray, purpose: FingerprintPurpose, kind: FingerprintKind) =
        FingerprintV2.isCanonicalPreimage(bytes, purpose, kind)

    private fun hex(value: String): ByteArray {
        val compact = value.filterNot(Char::isWhitespace)
        require(compact.length % 2 == 0)
        return compact.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private companion object {
        val STABLE = FingerprintPurpose.STABLE_INTENT
        val ATTEMPT = FingerprintPurpose.DELIVERY_ATTEMPT
        val COMPLETE = FingerprintKind.COMPLETE_SET
        val SKIPPED = FingerprintKind.SET_EXERCISE_SKIPPED
        val OTHER_EXERCISE = ProtocolFixtures.uuid("ffeeddcc-bbaa-9988-7766-554433221101")

        const val HEADER_BYTES = 10

        const val COMPLETE_WEIGHTED_STABLE_PREIMAGE =
            "574b574600020101000c01000000066e6f64652d410200000004000000020300000010001122334455667788" +
                "99aabbccddeeff0400000010102132435465768798a9bacbdcedfe0f0500000010112233445566778899aabb" +
                "ccddeeff000600000008000000000000002a0700000010ffeeddccbbaa998877665544332211000800000004" +
                "0000000309000000040000000c0a000000050100001c520b00000001010c0000000102"
        const val COMPLETE_WEIGHTED_STABLE_FINGERPRINT =
            "00026aa0d02ba3e826906ee337448b6b3edc2e8449e22c651b3a6f89b8225b549ecb"
        const val COMPLETE_WEIGHTED_ATTEMPT_PREIMAGE =
            "574b574600020201000e01000000066e6f64652d410200000004000000020300000010001122334455667788" +
                "99aabbccddeeff0400000010102132435465768798a9bacbdcedfe0f0500000010112233445566778899aabb" +
                "ccddeeff000600000008000000000000002a0700000010ffeeddccbbaa998877665544332211000800000004" +
                "0000000309000000040000000c0a000000050100001c520b00000001010c00000001020d00000010abcdef01" +
                "23456789abcdef01234567890e000000080000000000000009"
        const val COMPLETE_WEIGHTED_ATTEMPT_FINGERPRINT =
            "0002757d4ab6204f0b560a3296e21bc4f2c5172bea1c00fb444884f7c6acb51ac89a"
        const val COMPLETE_WEIGHTLESS_STABLE_PREIMAGE =
            "574b574600020101000c010000000877617463682dceb20200000004000000020300000010fedcba98765432" +
                "10fedcba987654321004000000100102030405060708090a0b0c0d0e0f100500000010111111112222333384" +
                "445555555555550600000008000346dc5d6388650700000010aaaaaaaabbbb4ccc8dddeeeeeeeeeeee080000" +
                "0004000000000900000004000000010a00000001000b00000001020c0000000101"
        const val COMPLETE_WEIGHTLESS_STABLE_FINGERPRINT =
            "0002924d0908bf5d45df8f5f81992f6facacf4686c8096dbed96b2b3d505c56dcc90"
        const val COMPLETE_WEIGHTLESS_ATTEMPT_PREIMAGE =
            "574b574600020201000e010000000877617463682dceb20200000004000000020300000010fedcba98765432" +
                "10fedcba987654321004000000100102030405060708090a0b0c0d0e0f100500000010111111112222333384" +
                "445555555555550600000008000346dc5d6388650700000010aaaaaaaabbbb4ccc8dddeeeeeeeeeeee080000" +
                "0004000000000900000004000000010a00000001000b00000001020c00000001010d00000010999999998888" +
                "477786665555555555550e00000008000000000000004d"
        const val COMPLETE_WEIGHTLESS_ATTEMPT_FINGERPRINT =
            "000287a82b86d724cfac4ce3c84c871b7d670050d37711c9f80b3da58892acc6e66d"
        const val SKIP_STABLE_PREIMAGE =
            "574b574600020102000801000000066e6f64652d410200000004000000020300000010001122334455667788" +
                "99aabbccddeeff0400000010102132435465768798a9bacbdcedfe0f0500000010112233445566778899aabb" +
                "ccddeeff000600000008000000000000002a0700000010ffeeddccbbaa998877665544332211000800000001" +
                "01"
        const val SKIP_STABLE_FINGERPRINT =
            "00028e9cfc373bfc2a6ee96a2d88e5557792b90a2c2cdbfb9636a2cdebdb4704dfcf"
        const val SKIP_ATTEMPT_PREIMAGE =
            "574b574600020202000a01000000066e6f64652d410200000004000000020300000010001122334455667788" +
                "99aabbccddeeff0400000010102132435465768798a9bacbdcedfe0f0500000010112233445566778899aabb" +
                "ccddeeff000600000008000000000000002a0700000010ffeeddccbbaa998877665544332211000800000001" +
                "010900000010abcdef0123456789abcdef01234567890a000000080000000000000009"
        const val SKIP_ATTEMPT_FINGERPRINT =
            "0002b732d9d1ca1f2e2af0e3b84f8b85e8ddf7157508884c5cccbd2348c40dee29c3"
        const val RETURN_STABLE_PREIMAGE =
            "574b5746000201020008010000000877617463682dceb20200000004000000020300000010fedcba98765432" +
                "10fedcba987654321004000000100102030405060708090a0b0c0d0e0f100500000010111111112222333384" +
                "445555555555550600000008000346dc5d6388650700000010aaaaaaaabbbb4ccc8dddeeeeeeeeeeee080000" +
                "000100"
        const val RETURN_STABLE_FINGERPRINT =
            "0002ce270675f96486da51ec110a1d7ea08224c344f1fc42df14a61f6bdc8e809d22"
        const val RETURN_ATTEMPT_PREIMAGE =
            "574b574600020202000a010000000877617463682dceb20200000004000000020300000010fedcba98765432" +
                "10fedcba987654321004000000100102030405060708090a0b0c0d0e0f100500000010111111112222333384" +
                "445555555555550600000008000346dc5d6388650700000010aaaaaaaabbbb4ccc8dddeeeeeeeeeeee080000" +
                "0001000900000010999999998888477786665555555555550a00000008000000000000004d"
        const val RETURN_ATTEMPT_FINGERPRINT =
            "00028a4d2be199039fadd9f3328ad5125f85cf2655032d9ac398d0a451b8b5523332"
    }
}
