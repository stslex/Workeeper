// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
internal class RecoveryScenarioTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `Continue defaults to denied for a stamped interrupted-restore Intent`() {
        val intent = RecoveryScenario.intent(context, RecoveryScenario.InterruptedRestore)

        assertEquals(RecoveryScenario.InterruptedRestore, RecoveryScenario.fromIntent(intent))
        assertFalse(RecoveryScenario.allowsContinue(intent))
    }

    @Test
    fun `explicit Continue opt-in round trips`() {
        val intent = RecoveryScenario.intent(
            context = context,
            scenario = RecoveryScenario.InterruptedRestore,
            allowContinue = true,
        )

        assertEquals(RecoveryScenario.InterruptedRestore, RecoveryScenario.fromIntent(intent))
        assertTrue(RecoveryScenario.allowsContinue(intent))
    }

    @Test
    fun `unstamped Intent keeps startup-migration and denied defaults`() {
        val intent = Intent()

        assertEquals(RecoveryScenario.StartupMigration, RecoveryScenario.fromIntent(intent))
        assertFalse(RecoveryScenario.allowsContinue(intent))
        assertFalse(RecoveryScenario.allowsContinue(null))
    }
}
