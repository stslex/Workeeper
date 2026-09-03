// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for [WearDataLayerApiRule]. GUARD: the rule matches AST references only, which is why
 * this file's own triple-quoted fixtures — string literals naming the same package — are not
 * flagged when detekt runs over `lint-rules` itself.
 */
internal class WearDataLayerApiRuleTest {

    private val rule = WearDataLayerApiRule()

    @Test
    fun `flags the fully qualified call that ForbiddenImport cannot see`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.wear.tile

            fun bypass(context: android.content.Context): Any =
                com.google.android.gms.wearable.Wearable.getMessageClient(context)
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "Expected one finding for the reference, got: $findings")
        assertTrue(findings.single().message.contains("com.google.android.gms.wearable"))
    }

    @Test
    fun `flags a fully qualified type reference`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.wear_bridge

            class Transport(private val client: com.google.android.gms.wearable.MessageClient)
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "Expected one finding for the type, got: $findings")
    }

    @Test
    fun `flags a subpackage reference`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.wear.tile

            val internals: Any = com.google.android.gms.wearable.internal.Probe
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "Expected one finding for the subpackage, got: $findings")
    }

    @Test
    fun `one reference is one finding, however long the qualified chain`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.wear.tile

            fun send(context: android.content.Context) {
                com.google.android.gms.wearable.Wearable
                    .getMessageClient(context)
                    .sendMessage("node", "/path", ByteArray(0))
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "A nested chain must not report per segment: $findings")
    }

    @Test
    fun `does not double up on an import, which ForbiddenImport owns`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.wear.tile

            import com.google.android.gms.wearable.Wearable

            val client: Any = Wearable
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "The import directive belongs to ForbiddenImport: $findings")
    }

    @Test
    fun `leaves unrelated fully qualified references alone`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.wear.tile

            val services: Any = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val wearable: Any = androidx.wear.tiles.TileService::class
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "Only the Data Layer package is forbidden: $findings")
    }

    @Test
    fun `a package that merely starts with the same characters is not a match`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.wear.tile

            val decoy: Any = com.google.android.gms.wearablefake.Client.get()
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "The match must be on a package boundary: $findings")
    }
}
