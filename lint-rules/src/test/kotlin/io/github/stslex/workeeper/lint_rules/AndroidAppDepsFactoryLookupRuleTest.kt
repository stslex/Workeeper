// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AndroidAppDepsFactoryLookupRuleTest {

    @Test
    fun rejectsAppDepsThroughDirectAliasAndStarImports() {
        val examples = listOf(
            "import $APP_DEPS" to "appDeps",
            "import $APP_DEPS as lookup" to "lookup",
            "import ${APP_DEPS.substringBeforeLast('.')}.*" to "appDeps",
        )
        for ((directive, name) in examples) {
            val findings = lint(
                """
                $directive
                interface SettingsGraph {
                    interface Factory
                }
                typealias SettingsFactory = SettingsGraph.Factory
                fun read(context: android.content.Context): SettingsFactory = context.$name<SettingsFactory>()
                """.trimIndent(),
            )
            assertEquals(1, findings.size)
        }
    }

    @Test
    fun rejectsSuppressedLookup() {
        val content = """
            @file:Suppress("AndroidAppDepsFactoryLookupRule")

            import $APP_DEPS

            fun read(context: android.content.Context): SettingsGraph.Factory = context.appDeps<SettingsGraph.Factory>()
        """.trimIndent()
        assertEquals(1, lint(content).size)
    }

    @Test
    fun permitsInjectedFactoryAndUnrelatedSameName() {
        val content = """
            import unrelated.appDeps

            interface InjectedFactories { fun <T : Any> appDeps(): T }
            fun read(owner: InjectedFactories): SettingsGraph.Factory = owner.appDeps<SettingsGraph.Factory>()
            fun use(factory: SettingsGraph.Factory): SettingsGraph.Factory = factory
        """.trimIndent()
        assertEquals(0, lint(content).size)
    }

    @Test
    fun distinguishesAnImportedContextLookupFromAnUnrelatedLocalExtension() {
        val content = """
            import android.content.Context
            import $APP_DEPS

            interface SettingsGraph { interface Factory }
            interface InjectedFactories { val factory: SettingsGraph.Factory }

            private inline fun <reified T : Any> InjectedFactories.appDeps(): T = factory as T

            fun forbidden(context: Context): SettingsGraph.Factory = context.appDeps<SettingsGraph.Factory>()
            fun allowed(other: InjectedFactories): SettingsGraph.Factory = other.appDeps<SettingsGraph.Factory>()
        """.trimIndent()
        val findings = lint(content)
        assertEquals(1, findings.size)
        assertEquals(
            content.lineSequence().indexOfFirst { it.startsWith("fun forbidden") } + 1,
            findings.single().entity.location.source.line,
        )
    }

    private fun lint(content: String) = AndroidAppDepsFactoryLookupRule().lint(content)

    private companion object {
        const val APP_DEPS = "io.github.stslex.workeeper.core.ui.mvi.di.appDeps"
    }
}
