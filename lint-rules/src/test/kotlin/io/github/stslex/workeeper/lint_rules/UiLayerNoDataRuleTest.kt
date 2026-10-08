// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.github.detekt.test.utils.compileContentForTest
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for [UiLayerNoDataRule] scoping: production `/ui/` files are inspected, test source sets
 * are not. GUARD: the rule keys on the file path, so every fixture is compiled at a path.
 */
internal class UiLayerNoDataRuleTest {

    private val rule = UiLayerNoDataRule()

    @Test
    fun `flags core data model import in main feature ui`() {
        assertProductionSourceSetFlagged("main")
    }

    @Test
    fun `flags core data model import in commonMain feature ui`() {
        assertProductionSourceSetFlagged("commonMain")
    }

    @Test
    fun `allows core data model import in commonTest ui sources`() {
        assertTestSourceSetExempt("commonTest")
    }

    @Test
    fun `allows core data model import in iosTest ui sources`() {
        assertTestSourceSetExempt("iosTest")
    }

    @Test
    fun `allows core data model import in androidHostTest ui sources`() {
        assertTestSourceSetExempt("androidHostTest")
    }

    @Test
    fun `allows core data model import in androidDeviceTest ui sources`() {
        assertTestSourceSetExempt("androidDeviceTest")
    }

    @Test
    fun `flags core data model import in main ui of a checkout under a test-named src directory`() {
        // detekt passes the absolute path, so directories above the checkout are part of it.
        val checkout = "/home/dev/src/testProjects/Workeeper/feature/example"
        val findings = rule.lintForPath(
            "$checkout/src/main/kotlin/io/github/stslex/workeeper/feature/example/ui/ExampleScreen.kt",
            """
            package io.github.stslex.workeeper.feature.example.ui

            import io.github.stslex.workeeper.core.data.example.model.ExampleDataModel

            fun ExampleScreen(item: ExampleDataModel) = Unit
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "only the module's own src/<set> counts, got: $findings")
    }

    private fun assertProductionSourceSetFlagged(sourceSet: String) {
        val findings = rule.lintForPath(
            "src/$sourceSet/kotlin/io/github/stslex/workeeper/feature/example/ui/ExampleScreen.kt",
            """
            package io.github.stslex.workeeper.feature.example.ui

            import io.github.stslex.workeeper.core.data.example.model.ExampleDataModel

            fun ExampleScreen(item: ExampleDataModel) = Unit
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "src/$sourceSet is production code, got: $findings")
        assertTrue(findings.single().message.contains("ExampleDataModel"))
    }

    private fun assertTestSourceSetExempt(sourceSet: String) {
        val findings = rule.lintForPath(
            "src/$sourceSet/kotlin/io/github/stslex/workeeper/feature/example/ui/ExampleScreenTest.kt",
            """
            package io.github.stslex.workeeper.feature.example.ui

            import io.github.stslex.workeeper.core.data.example.model.ExampleDataModel

            class ExampleScreenTest { fun stub(): ExampleDataModel? = null }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "src/$sourceSet is a test source set, got: $findings")
    }

    private fun UiLayerNoDataRule.lintForPath(
        virtualPath: String,
        content: String,
    ) = lint(compileContentForTest(content, virtualPath))
}
