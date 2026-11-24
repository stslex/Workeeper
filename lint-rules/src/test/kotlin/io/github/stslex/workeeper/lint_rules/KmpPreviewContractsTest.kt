package io.github.stslex.workeeper.lint_rules

import io.github.detekt.test.utils.compileContentForTest
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class KmpPreviewContractsTest {
    @Test
    fun `records the real subject and forwarded theme without entering production code`() {
        assertEquals(listOf("sample.Card@LIGHT"), render(
            "fun Preview() { helper(ThemeMode.LIGHT) }; private fun helper(mode: ThemeMode) { AppTheme(themeMode = mode) { Card() } }",
        ))
        assertEquals(listOf("sample.Card@DARK"), render(
            "fun Preview() { helper(mode = ThemeMode.DARK) }; private fun helper(mode: ThemeMode) { AppTheme(themeMode = mode) { Card() } }",
        ))
    }

    @Test
    fun `subject substitution missing subject and wrong theme fail the expected contract`() {
        val expected = listOf("sample.Card@LIGHT")
        for (body in listOf(
            "AppTheme(themeMode = ThemeMode.LIGHT) { Text(\"empty\") }",
            "AppTheme(themeMode = ThemeMode.DARK) { Card() }",
            "AppTheme(themeMode = ThemeMode.LIGHT) {}; Card()",
            "AppTheme(themeMode = ThemeMode.LIGHT) { val unused = { Card() } }",
            "AppTheme(themeMode = ThemeMode.LIGHT) { if (true) return@AppTheme; Card() }",
        )) assertNotEquals(expected, render("fun Preview() { $body }"))
    }

    @Test
    fun `default SYSTEM and qualified member subjects are retained`() {
        assertEquals(listOf("sample.AppButton.Primary@SYSTEM"), render(
            "fun Preview() { AppTheme { AppButton.Primary() } }",
            subjects = setOf("sample.AppButton.Primary"),
        ))
    }

    @Test
    fun `renamed helper named arguments layout and local theme alias preserve coverage`() {
        assertEquals(listOf("sample.Card@LIGHT"), render(
            """
            fun Preview() { renamed(theme = ThemeMode.LIGHT) }
            private fun renamed(theme: ThemeMode) {
                val mode = theme
                AppTheme(themeMode = mode) { Column { Card() } }
            }
            """.trimIndent(),
        ))
    }

    @Test
    fun `fake local AppTheme cannot satisfy real theme scope`() {
        assertNotEquals(listOf("sample.Card@LIGHT"), render(
            "fun Preview() { AppTheme(ThemeMode.LIGHT) { Card() } }; private fun AppTheme(mode: ThemeMode, content: () -> Unit) { content() }",
        ))
    }

    @Test
    fun `real qualified and aliased Preview annotations remain identifiable`() {
        for (source in listOf(
            "@androidx.compose.ui.tooling.preview.Preview fun Preview() {}",
            "import androidx.compose.ui.tooling.preview.Preview as Sample; @Sample fun Preview() {}",
        )) {
            val file = compileContentForTest(source)
            val function = file.collectDescendantsOfType<KtNamedFunction>().single()
            assertEquals(true, KmpPreviewContracts.hasAnnotation(function, KmpPreviewContracts.PREVIEW))
        }
        val fake = compileContentForTest(
            "package sample; import androidx.compose.ui.tooling.preview.Preview; annotation class Preview; @Preview fun example() {}",
        )
        assertEquals(false, KmpPreviewContracts.hasAnnotation(
            fake.collectDescendantsOfType<KtNamedFunction>().single(), KmpPreviewContracts.PREVIEW,
        ))
    }

    @Test
    fun `removing one rendered subject fails the multi subject contract`() {
        val expected = listOf("sample.Card@LIGHT", "sample.Other@LIGHT")
        val subjects = setOf("sample.Card", "sample.Other")
        assertEquals(expected, render("fun Preview() { AppTheme(ThemeMode.LIGHT) { Card(); Other() } }", subjects))
        assertNotEquals(expected, render("fun Preview() { AppTheme(ThemeMode.LIGHT) { Card() } }", subjects))
    }

    @Test
    fun `review mutable theme alias cannot claim its old value`() {
        assertNotEquals(listOf("sample.Card@LIGHT"), render(
            "fun Preview() { var mode = ThemeMode.LIGHT; mode = ThemeMode.DARK; AppTheme(mode) { Card() } }",
        ))
    }

    @Test
    fun `review early return cannot claim an unreachable subject`() {
        assertNotEquals(listOf("sample.Card@LIGHT"), render(
            "fun Preview() { AppTheme(ThemeMode.LIGHT) { return@AppTheme; Card() } }",
        ))
    }

    @Test
    fun `review local subject shadow cannot stand in for production`() {
        assertNotEquals(listOf("sample.Card@LIGHT"), render(
            "fun Preview() { fun Card() {}; AppTheme(ThemeMode.LIGHT) { Card() } }",
        ))
    }

    @Test
    fun `review mixed arguments bind their actual positions`() {
        assertEquals(listOf("sample.Card@DARK"), render(
            """
            fun Preview() { helper(ignored = ThemeMode.LIGHT, ThemeMode.DARK) }
            private fun helper(ignored: ThemeMode, mode: ThemeMode = ThemeMode.LIGHT) {
                AppTheme(mode) { Card() }
            }
            """.trimIndent(),
        ))
    }

    @Test
    fun `unknown callable values cannot impersonate terminal subjects`() {
        assertNotEquals(listOf("sample.Card@LIGHT"), render(
            """
            fun Preview() { val Card = callback(); AppTheme(ThemeMode.LIGHT) { Card() } }
            private fun callback(): () -> Unit = {}
            """.trimIndent(),
        ))
    }

    @Test
    fun `review qualified selector cannot invoke a different content binding`() {
        assertNotEquals(listOf("sample.Card@LIGHT"), render(
            "fun Preview() { helper { Card() } }; private fun helper(content: () -> Unit) { AppTheme(ThemeMode.LIGHT) { Sink.content() } }; object Sink { fun content() {} }",
        ))
        assertEquals(listOf("sample.Card@LIGHT"), render(
            "fun Preview() { helper { Card() } }; private fun helper(content: () -> Unit) { AppTheme(ThemeMode.LIGHT) { content() } }",
        ))
    }

    private fun render(body: String, subjects: Set<String> = setOf("sample.Card")): List<String> {
        val file = compileContentForTest(
            """
            package sample
            import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
            import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
            import androidx.compose.foundation.layout.Column
            fun Card() { val unrelated = ThemeMode.DARK; val fallback = ThemeMode.SYSTEM }
            $body
            """.trimIndent(),
        )
        val preview = file.collectDescendantsOfType<KtNamedFunction>().single { it.name == "Preview" }
        return KmpPreviewContracts(listOf(file), subjects).render(preview)
    }
}
