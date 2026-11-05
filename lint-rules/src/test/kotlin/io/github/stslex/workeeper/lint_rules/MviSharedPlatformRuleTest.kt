// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.github.detekt.test.utils.compileContentForTest
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MviSharedPlatformRuleTest {

    @Test
    fun `rejects a real platform import with a local alias`() {
        val findings = lint("commonMain", "import android.content.Context as PlatformContext")
        assertEquals(1, findings.size)
    }

    @Test
    fun `permits a comment or a string naming the platform API`() {
        val findings = lint(
            "commonMain",
            """
            // import android.content.Context
            val hint = "import android.content.Context"
            """.trimIndent(),
        )
        assertEquals(0, findings.size)
    }

    @Test
    fun `permits platform imports in the Android source set`() {
        assertEquals(0, lint("androidMain", "import android.content.Context").size)
    }

    @Test
    fun `rejects moving the holder declaration into shared code`() {
        assertEquals(1, lint("iosMain", "interface AppDepsHolder").size)
    }

    @Test
    fun `rejects restoring the legacy lifetime type in production`() {
        assertEquals(1, lint("androidMain", "typealias StoreGenerationDeps = Any").size)
        assertEquals(0, lint("androidHostTest", "val name = \"StoreGenerationDeps\"").size)
    }

    private fun lint(sourceSet: String, content: String) =
        MviSharedPlatformRule().lint(
            compileContentForTest(content, "/project/core/ui/mvi/src/$sourceSet/kotlin/Fixture.kt"),
        )
}
