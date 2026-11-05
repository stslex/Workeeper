// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.github.detekt.test.utils.compileContentForTest
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MviSharedPlatformRuleTest {

    @Test
    fun `rejects a real platform import with a local alias`() {
        val findings = lint("sharedProductionFiles", "import android.content.Context as PlatformContext")
        assertEquals(1, findings.size)
    }

    @Test
    fun `permits a comment or a string naming the platform API`() {
        val findings = lint(
            "sharedProductionFiles",
            """
            // import android.content.Context
            val hint = "import android.content.Context"
            """.trimIndent(),
        )
        assertEquals(0, findings.size)
    }

    @Test
    fun `permits platform imports in the Android source set`() {
        assertEquals(0, lint("productionFiles", "import android.content.Context\ninterface AppDepsHolder").size)
    }

    @Test
    fun `rejects moving the holder declaration into shared code`() {
        assertEquals(1, lint("sharedProductionFiles", "interface AppDepsHolder").size)
    }

    @Test
    fun `rejects restoring the legacy lifetime type in production`() {
        assertEquals(1, lint("productionFiles", "typealias StoreGenerationDeps = Any").size)
        assertEquals(0, lint("testFiles", "typealias StoreGenerationDeps = Any").size)
    }

    @Test
    fun `file and declaration suppression cannot retire the check`() {
        val suppressions = listOf(
            "@file:Suppress(\"MviSharedPlatformRule\")",
            "@Suppress(\"MviSharedPlatformRule\")",
        )
        for (suppression in suppressions) {
            assertEquals(1, lint("productionFiles", "$suppression\n\ntypealias StoreGenerationDeps = Any").size)
        }
    }

    @Test
    fun `holder remains Android only when shared tests are in a custom directory`() {
        val imported = "import io.github.stslex.workeeper.core.ui.mvi.di.AppDepsHolder"
        assertEquals(1, lint("sharedTestFiles", imported).size)
    }

    private fun lint(scope: String, content: String) =
        MviSharedPlatformRule(TestConfig(scope to listOf(FIXTURE))).lint(compileContentForTest(content, FIXTURE))

    private companion object {
        const val FIXTURE = "/project/custom/Fixture.kt"
    }
}
