// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FingerprintV1Test {

    @Test
    fun `weighted known-answer vectors retain full stable and attempt digests`() {
        assertVector(
            command = weightedCommand(),
            purpose = FingerprintPurpose.STABLE_INTENT,
            expectedPreimage = hex(WEIGHTED_STABLE_PREIMAGE),
            expectedFingerprint = hex(WEIGHTED_STABLE_FINGERPRINT),
        )
        assertVector(
            command = weightedCommand(),
            purpose = FingerprintPurpose.DELIVERY_ATTEMPT,
            expectedPreimage = hex(WEIGHTED_ATTEMPT_PREIMAGE),
            expectedFingerprint = hex(WEIGHTED_ATTEMPT_FINGERPRINT),
        )
    }

    @Test
    fun `weightless null known-answer vectors use one-byte null tag`() {
        assertVector(
            command = weightlessCommand(),
            purpose = FingerprintPurpose.STABLE_INTENT,
            expectedPreimage = hex(WEIGHTLESS_STABLE_PREIMAGE),
            expectedFingerprint = hex(WEIGHTLESS_STABLE_FINGERPRINT),
        )
        assertVector(
            command = weightlessCommand(),
            purpose = FingerprintPurpose.DELIVERY_ATTEMPT,
            expectedPreimage = hex(WEIGHTLESS_ATTEMPT_PREIMAGE),
            expectedFingerprint = hex(WEIGHTLESS_ATTEMPT_FINGERPRINT),
        )
    }

    @Test
    fun `UUID spelling is not fingerprint input and every canonical field changes its result`() {
        val base = weightedCommand()
        val upperCaseUuid = base.copy(
            commandId = CanonicalUuid.parse("00112233-4455-6677-8899-AABBCCDDEEFF"),
        )
        assertContentEquals(
            FingerprintV1.fingerprint(base, FingerprintPurpose.STABLE_INTENT).encoded,
            FingerprintV1.fingerprint(upperCaseUuid, FingerprintPurpose.STABLE_INTENT).encoded,
        )

        val mutations = listOf(
            base.copy(sourceNodeId = "node-B"),
            base.copy(schemaVersion = 2),
            base.copy(commandId = ProtocolFixtures.uuid("00112233-4455-6677-8899-aabbccddee00")),
            base.copy(databaseEpoch = ProtocolFixtures.uuid("10213243-5465-7687-98a9-bacbdcedfe00")),
            base.copy(sessionUuid = ProtocolFixtures.uuid("11223344-5566-7788-99aa-bbccddeeff01")),
            base.copy(sessionRevision = 43),
            base.copy(performedExerciseUuid = ProtocolFixtures.uuid("ffeeddcc-bbaa-9988-7766-554433221101")),
            base.copy(setPosition = 4),
            base.copy(reps = 13),
            base.copy(weightHundredthsKg = null),
            base.copy(weightHundredthsKg = 0),
            base.copy(exerciseType = ExerciseTypeWire.WEIGHTLESS, weightHundredthsKg = null),
            base.copy(setType = SetTypeWire.DROP),
        )
        val baseline = FingerprintV1.fingerprint(base, FingerprintPurpose.STABLE_INTENT)
        mutations.forEach { mutation ->
            assertNotEquals(
                baseline,
                FingerprintV1.fingerprint(mutation, FingerprintPurpose.STABLE_INTENT),
            )
        }
        assertNotEquals(
            baseline,
            FingerprintV1.fingerprint(base, FingerprintPurpose.DELIVERY_ATTEMPT),
        )
    }

    @Test
    fun `attempt fingerprint includes both lease fields while stable intent excludes them`() {
        val base = weightedCommand()
        val changedLease = base.copy(
            mutationLeaseId = ProtocolFixtures.uuid("abcdef01-2345-6789-abcd-ef0123456700"),
            mutationLeaseGeneration = 10,
        )
        assertContentEquals(
            FingerprintV1.fingerprint(base, FingerprintPurpose.STABLE_INTENT).encoded,
            FingerprintV1.fingerprint(changedLease, FingerprintPurpose.STABLE_INTENT).encoded,
        )
        assertNotEquals(
            FingerprintV1.fingerprint(base, FingerprintPurpose.DELIVERY_ATTEMPT),
            FingerprintV1.fingerprint(changedLease, FingerprintPurpose.DELIVERY_ATTEMPT),
        )
    }

    @Test
    fun `strict preimage parser rejects order count length enum and truncation changes`() {
        val valid = FingerprintV1.preimage(weightedCommand(), FingerprintPurpose.STABLE_INTENT)
        assertTrue(FingerprintV1.isCanonicalPreimage(valid, FingerprintPurpose.STABLE_INTENT))

        val wrongPurpose = valid.copyOf().also { it[6] = 0x02 }
        val wrongCount = valid.copyOf().also { it[8] = 0x0d }
        val wrongFirstTag = valid.copyOf().also { it[9] = 0x02 }
        val wrongFirstLength = valid.copyOf().also { it[13] = 0x07 }
        val wrongExerciseEnum = valid.copyOf().also { bytes ->
            bytes[indexOfTagValue(bytes, tag = 0x0b)] = 0x00
        }

        listOf(
            wrongPurpose,
            wrongCount,
            wrongFirstTag,
            wrongFirstLength,
            wrongExerciseEnum,
            valid.copyOf(valid.size - 1),
            valid + 0x00.toByte(),
        ).forEach { malformed ->
            assertFalse(FingerprintV1.isCanonicalPreimage(malformed, FingerprintPurpose.STABLE_INTENT))
        }
    }

    @Test
    fun `stored value rejects truncated digest and unsupported version`() {
        assertFailsWith<IllegalArgumentException> {
            FingerprintValue.parse(ByteArray(FingerprintValue.ENCODED_SIZE - 1))
        }
        assertFailsWith<UnsupportedFingerprintVersionException> {
            FingerprintValue.parse(ByteArray(FingerprintValue.ENCODED_SIZE).also { it[1] = 0x02 })
        }
    }

    private fun assertVector(
        command: FingerprintCommand,
        purpose: FingerprintPurpose,
        expectedPreimage: ByteArray,
        expectedFingerprint: ByteArray,
    ) {
        val actualPreimage = FingerprintV1.preimage(command, purpose)
        assertContentEquals(expectedPreimage, actualPreimage)
        assertTrue(FingerprintV1.isCanonicalPreimage(actualPreimage, purpose))
        assertContentEquals(expectedFingerprint, FingerprintV1.fingerprint(command, purpose).encoded)
        assertTrue(
            FingerprintValue.parse(expectedFingerprint).constantTimeEquals(
                FingerprintV1.fingerprint(command, purpose),
            ),
        )
    }

    private fun indexOfTagValue(bytes: ByteArray, tag: Int): Int {
        var index = 9
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

    private fun weightedCommand() = FingerprintCommand(
        sourceNodeId = "node-A",
        schemaVersion = 1,
        commandId = ProtocolFixtures.uuid("00112233-4455-6677-8899-aabbccddeeff"),
        databaseEpoch = ProtocolFixtures.uuid("10213243-5465-7687-98a9-bacbdcedfe0f"),
        sessionUuid = ProtocolFixtures.uuid("11223344-5566-7788-99aa-bbccddeeff00"),
        sessionRevision = 42,
        performedExerciseUuid = ProtocolFixtures.uuid("ffeeddcc-bbaa-9988-7766-554433221100"),
        setPosition = 3,
        reps = 12,
        weightHundredthsKg = 7_250,
        exerciseType = ExerciseTypeWire.WEIGHTED,
        setType = SetTypeWire.WORK,
        mutationLeaseId = ProtocolFixtures.uuid("abcdef01-2345-6789-abcd-ef0123456789"),
        mutationLeaseGeneration = 9,
    )

    private fun weightlessCommand() = FingerprintCommand(
        sourceNodeId = "watch-β",
        schemaVersion = 1,
        commandId = ProtocolFixtures.uuid("fedcba98-7654-3210-fedc-ba9876543210"),
        databaseEpoch = ProtocolFixtures.uuid("01020304-0506-0708-090a-0b0c0d0e0f10"),
        sessionUuid = ProtocolFixtures.uuid("11111111-2222-3333-8444-555555555555"),
        sessionRevision = 922_337_203_685_477,
        performedExerciseUuid = ProtocolFixtures.uuid("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"),
        setPosition = 0,
        reps = 1,
        weightHundredthsKg = null,
        exerciseType = ExerciseTypeWire.WEIGHTLESS,
        setType = SetTypeWire.WARM,
        mutationLeaseId = ProtocolFixtures.uuid("99999999-8888-4777-8666-555555555555"),
        mutationLeaseGeneration = 77,
    )

    private fun hex(value: String): ByteArray {
        val compact = value.filterNot(Char::isWhitespace)
        require(compact.length % 2 == 0)
        return compact.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private companion object {
        const val WEIGHTED_STABLE_PREIMAGE =
            "574b5746000101000c01000000066e6f64652d410200000004000000010300000010" +
                "00112233445566778899aabbccddeeff0400000010102132435465768798a9bacbdcedfe0f" +
                "0500000010112233445566778899aabbccddeeff000600000008000000000000002a" +
                "0700000010ffeeddccbbaa998877665544332211000800000004000000030900000004" +
                "0000000c0a000000050100001c520b00000001010c0000000102"
        const val WEIGHTED_STABLE_FINGERPRINT =
            "0001a4ee744657718b4551e6320bf1173ee64a8cd65deafc576441f88b6afe420b0f"
        val WEIGHTED_ATTEMPT_PREIMAGE = WEIGHTED_STABLE_PREIMAGE
            .replace("574b5746000101000c", "574b5746000102000e") +
            "0d00000010abcdef0123456789abcdef01234567890e000000080000000000000009"
        const val WEIGHTED_ATTEMPT_FINGERPRINT =
            "00016dacd39afc4a864386359167ab49eb315f7bb962baf8a8bcdac7df8e5968af43"

        const val WEIGHTLESS_STABLE_PREIMAGE =
            "574b5746000101000c010000000877617463682dceb20200000004000000010300000010" +
                "fedcba9876543210fedcba987654321004000000100102030405060708090a0b0c0d0e0f10" +
                "0500000010111111112222333384445555555555550600000008000346dc5d638865" +
                "0700000010aaaaaaaabbbb4ccc8dddeeeeeeeeeeee0800000004000000000900000004" +
                "000000010a00000001000b00000001020c0000000101"
        const val WEIGHTLESS_STABLE_FINGERPRINT =
            "0001d0ba1f592870ac908044d843d37b2d182959ce3d798fe114c6b16a8cb5992750"
        val WEIGHTLESS_ATTEMPT_PREIMAGE = WEIGHTLESS_STABLE_PREIMAGE
            .replace("574b5746000101000c", "574b5746000102000e") +
            "0d00000010999999998888477786665555555555550e00000008000000000000004d"
        const val WEIGHTLESS_ATTEMPT_FINGERPRINT =
            "0001e30c911520fcbd2ac5e45759fa2a126ceb2d5f2bcbd8ac18c955d55a364eebdb"
    }
}
