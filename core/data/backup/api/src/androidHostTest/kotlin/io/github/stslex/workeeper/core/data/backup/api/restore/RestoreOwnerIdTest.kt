// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.restore

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class RestoreOwnerIdTest {

    @Test
    fun `production no-effects owner is not a valid protocol identity`() {
        assertThrows<IllegalArgumentException> {
            RestoreOwnerId("no-effects")
        }
    }
}
