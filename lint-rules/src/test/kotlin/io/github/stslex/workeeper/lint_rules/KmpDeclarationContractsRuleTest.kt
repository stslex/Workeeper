package io.github.stslex.workeeper.lint_rules

import io.github.detekt.test.utils.compileContentForTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import java.io.File

internal class KmpDeclarationContractsRuleTest {
    @TempDir
    lateinit var fixture: File

    @Test
    fun `new expect and suppression declarations change the inventory`() {
        val baseline = inventory("class A")
        assertNotEquals(baseline, inventory("expect class A"))
        assertNotEquals(baseline, inventory("@Suppress(\"Unchecked\") class A"))
    }

    @Test
    fun `file annotation aliases preserve diagnostic and target identity`() {
        val original = inventory("@file:Suppress(\"A\", \"B\")\npackage sample\nclass A")
        val alias = inventory("@file:Quiet(\"B\", \"A\")\npackage sample\nimport kotlin.Suppress as Quiet\nclass A")
        assertEquals(original, alias)
        assertNotEquals(original, inventory("package sample\n@Suppress(\"A\", \"B\") class A"))
    }

    @Test
    fun `preview annotation ordering import aliases and helper extraction are permitted`() {
        val original = inventory("""
            import androidx.compose.ui.tooling.preview.Preview
            import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
            import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
            @Preview(name = "Light", showBackground = true)
            fun CardPreview() { AppTheme(themeMode = ThemeMode.LIGHT) { Card() } }
        """.trimIndent())
        val refactored = inventory("""
            import androidx.compose.ui.tooling.preview.Preview as Sample
            import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme as Theme
            import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode as Mode
            @Sample(showBackground = true, name = "Light")
            fun CardPreview() { helper() }
            private fun helper() { Theme(themeMode = Mode.LIGHT) { Card() } }
        """.trimIndent())
        assertEquals(original, refactored)
        assertTrue(original.single().endsWith("subjects=Card@LIGHT"))
    }

    @Test
    fun `missing preview wrong theme and missing theme wrapper change inventory`() {
        val source = """
            import androidx.compose.ui.tooling.preview.Preview
            import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
            import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
            @Preview fun CardPreview() { AppTheme(themeMode = ThemeMode.LIGHT) { Card() } }
        """.trimIndent()
        val baseline = inventory(source)
        for (mutation in listOf("", source.replace("LIGHT", "DARK"), source.replace("AppTheme", "Box"))) {
            assertNotEquals(baseline, inventory(mutation))
        }
    }

    @Test
    fun `deleting a file cannot erase its reviewed declaration obligation`() {
        val first = fixture.resolve("A.kt").apply { writeText("package fixture; class A") }
        val removed = fixture.resolve("B.kt").apply { writeText("package fixture; expect class B") }
        val expected = inventory(removed.readText())
        check(removed.delete())
        val rule = KmpDeclarationContractsRule(TestConfig(
            "moduleDirectory" to fixture.absolutePath,
            "allFiles" to listOf("A.kt"),
            "commonProductionFiles" to listOf("A.kt"),
            "expectedDeclarations" to expected,
        ))
        val findings = rule.lint(compileContentForTest(first.readText(), first.absolutePath))
        assertTrue(findings.any { "Missing reviewed declaration: expect|fixture.B" in it.message })
    }

    @Test
    fun `new suppression cannot suppress its own inventory finding`() {
        val source = "@file:Suppress(\"KmpDeclarationContractsRule\")\npackage fixture\nclass A"
        val first = fixture.resolve("A.kt").apply { writeText(source) }
        val rule = KmpDeclarationContractsRule(TestConfig(
            "allFiles" to listOf(first.absolutePath),
            "commonProductionFiles" to listOf(first.absolutePath),
            "expectedDeclarations" to emptyList<String>(),
        ))
        assertTrue(rule.lint(compileContentForTest(source, first.absolutePath))
            .any { "Unreviewed declaration: suppress" in it.message })
    }

    private fun inventory(source: String): List<String> = KmpDeclarationInventory.collect(
        listOf(DeclarationSource(compileContentForTest(source, "/configured/Source.kt"), common = true)),
        subjects = setOf("Card"),
        verifyPreviewBodies = true,
    )
}
