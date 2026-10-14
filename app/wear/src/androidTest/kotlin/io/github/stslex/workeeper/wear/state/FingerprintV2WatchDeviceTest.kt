// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.state

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.FingerprintCommand
import io.github.stslex.workeeper.core.wear.protocol.FingerprintPurpose
import io.github.stslex.workeeper.core.wear.protocol.FingerprintV2
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandBody
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

/** wear-phase-2-exercise-list.md §5.6: the host test's known answers, on the watch runtime. */
@Regression
@RunWith(AndroidJUnit4::class)
internal class FingerprintV2WatchDeviceTest {

    @Test
    fun watchRuntimeReproducesEveryFullFingerprintV2KnownAnswer() {
        assertVector(
            command = weightedCommand(),
            stable = COMPLETE_WEIGHTED_STABLE,
            attempt = COMPLETE_WEIGHTED_ATTEMPT,
        )
        assertVector(
            command = weightlessCommand(),
            stable = COMPLETE_WEIGHTLESS_STABLE,
            attempt = COMPLETE_WEIGHTLESS_ATTEMPT,
        )
        assertVector(command = weightedCommand().withSkipped(true), stable = SKIP_STABLE, attempt = SKIP_ATTEMPT)
        assertVector(command = weightlessCommand().withSkipped(false), stable = RETURN_STABLE, attempt = RETURN_ATTEMPT)
    }

    private fun assertVector(command: FingerprintCommand, stable: String, attempt: String) {
        assertArrayEquals(
            hex(stable),
            FingerprintV2.fingerprint(command, FingerprintPurpose.STABLE_INTENT).encoded,
        )
        assertArrayEquals(
            hex(attempt),
            FingerprintV2.fingerprint(command, FingerprintPurpose.DELIVERY_ATTEMPT).encoded,
        )
    }

    private fun weightedCommand() = FingerprintCommand(
        sourceNodeId = "node-A",
        schemaVersion = 2,
        commandId = uuid("00112233-4455-6677-8899-aabbccddeeff"),
        databaseEpoch = uuid("10213243-5465-7687-98a9-bacbdcedfe0f"),
        sessionUuid = uuid("11223344-5566-7788-99aa-bbccddeeff00"),
        sessionRevision = 42,
        body = WatchCommandBody.CompleteSet(
            performedExerciseUuid = uuid("ffeeddcc-bbaa-9988-7766-554433221100"),
            setPosition = 3,
            reps = 12,
            weightHundredthsKg = 7_250,
            exerciseType = ExerciseTypeWire.WEIGHTED,
            setType = SetTypeWire.WORK,
        ),
        mutationLeaseId = uuid("abcdef01-2345-6789-abcd-ef0123456789"),
        mutationLeaseGeneration = 9,
    )

    private fun weightlessCommand() = FingerprintCommand(
        sourceNodeId = "watch-β",
        schemaVersion = 2,
        commandId = uuid("fedcba98-7654-3210-fedc-ba9876543210"),
        databaseEpoch = uuid("01020304-0506-0708-090a-0b0c0d0e0f10"),
        sessionUuid = uuid("11111111-2222-3333-8444-555555555555"),
        sessionRevision = 922_337_203_685_477,
        body = WatchCommandBody.CompleteSet(
            performedExerciseUuid = uuid("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"),
            setPosition = 0,
            reps = 1,
            weightHundredthsKg = null,
            exerciseType = ExerciseTypeWire.WEIGHTLESS,
            setType = SetTypeWire.WARM,
        ),
        mutationLeaseId = uuid("99999999-8888-4777-8666-555555555555"),
        mutationLeaseGeneration = 77,
    )

    private fun FingerprintCommand.withSkipped(skipped: Boolean) =
        copy(body = WatchCommandBody.SetExerciseSkipped(body.performedExerciseUuid, skipped))

    private fun uuid(value: String): CanonicalUuid = CanonicalUuid.parse(value)

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    private companion object {
        const val COMPLETE_WEIGHTED_STABLE =
            "00026aa0d02ba3e826906ee337448b6b3edc2e8449e22c651b3a6f89b8225b549ecb"
        const val COMPLETE_WEIGHTED_ATTEMPT =
            "0002757d4ab6204f0b560a3296e21bc4f2c5172bea1c00fb444884f7c6acb51ac89a"
        const val COMPLETE_WEIGHTLESS_STABLE =
            "0002924d0908bf5d45df8f5f81992f6facacf4686c8096dbed96b2b3d505c56dcc90"
        const val COMPLETE_WEIGHTLESS_ATTEMPT =
            "000287a82b86d724cfac4ce3c84c871b7d670050d37711c9f80b3da58892acc6e66d"
        const val SKIP_STABLE =
            "00028e9cfc373bfc2a6ee96a2d88e5557792b90a2c2cdbfb9636a2cdebdb4704dfcf"
        const val SKIP_ATTEMPT =
            "0002b732d9d1ca1f2e2af0e3b84f8b85e8ddf7157508884c5cccbd2348c40dee29c3"
        const val RETURN_STABLE =
            "0002ce270675f96486da51ec110a1d7ea08224c344f1fc42df14a61f6bdc8e809d22"
        const val RETURN_ATTEMPT =
            "00028a4d2be199039fadd9f3328ad5125f85cf2655032d9ac398d0a451b8b5523332"
    }
}
