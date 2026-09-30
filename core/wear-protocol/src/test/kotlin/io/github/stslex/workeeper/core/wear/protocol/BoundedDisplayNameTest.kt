// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BoundedDisplayNameTest {

    @Test
    fun `preserves exact single-byte and multi-byte names at 512 bytes`() {
        assertEquals(
            BoundedDisplayName.Value("a".repeat(512)),
            BoundedDisplayName.from("a".repeat(512)),
        )
        assertEquals(
            BoundedDisplayName.Value("я".repeat(256)),
            BoundedDisplayName.from("я".repeat(256)),
        )
        assertEquals(
            BoundedDisplayName.Value("a".repeat(508) + "😀"),
            BoundedDisplayName.from("a".repeat(508) + "😀"),
        )
    }

    @Test
    fun `omits complete name rather than slicing a 513-byte or split code point value`() {
        assertEquals(
            BoundedDisplayName.Omitted(OmissionReason.TOO_LARGE),
            BoundedDisplayName.from("a".repeat(513)),
        )
        assertEquals(
            BoundedDisplayName.Omitted(OmissionReason.TOO_LARGE),
            BoundedDisplayName.from("a".repeat(509) + "😀"),
        )
        assertEquals(
            BoundedDisplayName.Omitted(OmissionReason.TOO_LARGE),
            BoundedDisplayName.from("я".repeat(257)),
        )
    }

    @Test
    fun `omits invalid unicode without replacement`() {
        val invalidHighSurrogate = "prefix\uD800suffix"
        val result = assertIs<BoundedDisplayName.Omitted>(
            BoundedDisplayName.from(invalidHighSurrogate),
        )
        assertEquals(OmissionReason.INVALID_UNICODE, result.reason)
    }
}
