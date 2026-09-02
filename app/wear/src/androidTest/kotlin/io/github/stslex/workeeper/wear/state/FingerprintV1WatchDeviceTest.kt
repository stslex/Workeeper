// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.state

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.FingerprintCommand
import io.github.stslex.workeeper.core.wear.protocol.FingerprintPurpose
import io.github.stslex.workeeper.core.wear.protocol.FingerprintV1
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

@Regression
@RunWith(AndroidJUnit4::class)
internal class FingerprintV1WatchDeviceTest {

    @Test
    fun watchRuntimeReproducesEveryFullFingerprintV1KnownAnswer() {
        assertVector(
            command = weightedCommand(),
            stable = WEIGHTED_STABLE,
            attempt = WEIGHTED_ATTEMPT,
        )
        assertVector(
            command = weightlessCommand(),
            stable = WEIGHTLESS_STABLE,
            attempt = WEIGHTLESS_ATTEMPT,
        )
    }

    private fun assertVector(command: FingerprintCommand, stable: String, attempt: String) {
        assertArrayEquals(
            hex(stable),
            FingerprintV1.fingerprint(command, FingerprintPurpose.STABLE_INTENT).encoded,
        )
        assertArrayEquals(
            hex(attempt),
            FingerprintV1.fingerprint(command, FingerprintPurpose.DELIVERY_ATTEMPT).encoded,
        )
    }

    private fun weightedCommand() = FingerprintCommand(
        sourceNodeId = "node-A",
        schemaVersion = 1,
        commandId = uuid("00112233-4455-6677-8899-aabbccddeeff"),
        databaseEpoch = uuid("10213243-5465-7687-98a9-bacbdcedfe0f"),
        sessionUuid = uuid("11223344-5566-7788-99aa-bbccddeeff00"),
        sessionRevision = 42,
        performedExerciseUuid = uuid("ffeeddcc-bbaa-9988-7766-554433221100"),
        setPosition = 3,
        reps = 12,
        weightHundredthsKg = 7_250,
        exerciseType = ExerciseTypeWire.WEIGHTED,
        setType = SetTypeWire.WORK,
        mutationLeaseId = uuid("abcdef01-2345-6789-abcd-ef0123456789"),
        mutationLeaseGeneration = 9,
    )

    private fun weightlessCommand() = FingerprintCommand(
        sourceNodeId = "watch-β",
        schemaVersion = 1,
        commandId = uuid("fedcba98-7654-3210-fedc-ba9876543210"),
        databaseEpoch = uuid("01020304-0506-0708-090a-0b0c0d0e0f10"),
        sessionUuid = uuid("11111111-2222-3333-8444-555555555555"),
        sessionRevision = 922_337_203_685_477,
        performedExerciseUuid = uuid("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"),
        setPosition = 0,
        reps = 1,
        weightHundredthsKg = null,
        exerciseType = ExerciseTypeWire.WEIGHTLESS,
        setType = SetTypeWire.WARM,
        mutationLeaseId = uuid("99999999-8888-4777-8666-555555555555"),
        mutationLeaseGeneration = 77,
    )

    private fun uuid(value: String): CanonicalUuid = CanonicalUuid.parse(value)

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    private companion object {
        const val WEIGHTED_STABLE =
            "0001a4ee744657718b4551e6320bf1173ee64a8cd65deafc576441f88b6afe420b0f"
        const val WEIGHTED_ATTEMPT =
            "00016dacd39afc4a864386359167ab49eb315f7bb962baf8a8bcdac7df8e5968af43"
        const val WEIGHTLESS_STABLE =
            "0001d0ba1f592870ac908044d843d37b2d182959ce3d798fe114c6b16a8cb5992750"
        const val WEIGHTLESS_ATTEMPT =
            "0001e30c911520fcbd2ac5e45759fa2a126ceb2d5f2bcbd8ac18c955d55a364eebdb"
    }
}
