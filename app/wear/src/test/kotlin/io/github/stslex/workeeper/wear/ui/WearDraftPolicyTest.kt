// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WearDraftPolicyTest {

    @Test
    fun `reps use exact unit steps without wrap or clamp`() {
        assertNull(WearDraftPolicy.decrementReps(0))
        assertEquals(0, WearDraftPolicy.decrementReps(1))
        assertEquals(999, WearDraftPolicy.incrementReps(998))
        assertNull(WearDraftPolicy.incrementReps(WearProtocol.MAX_WEAR_REPS))
    }

    @Test
    fun `weight uses exact 250-hundredths steps and explicit null boundary`() {
        assertEquals(0, WearDraftPolicy.incrementWeight(null))
        assertEquals(250, WearDraftPolicy.incrementWeight(0))
        assertNull(WearDraftPolicy.incrementWeight(99_999))
        assertEquals(null, WearDraftPolicy.decrementWeight(0)?.value)
        assertEquals(0, WearDraftPolicy.decrementWeight(250)?.value)
        assertNull(WearDraftPolicy.decrementWeight(249))
        assertNull(WearDraftPolicy.decrementWeight(null))
    }
}
