package io.github.stslex.workeeper.lint_rules

import io.github.detekt.test.utils.compileContentForTest
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class KmpSharedContractsRuleTest {

    @TempDir
    lateinit var fixture: File

    @Test
    fun `platform references reject aliases qualified types and qualified calls`() {
        for (body in listOf(
            "import android.content.Context as C\nclass User(val context: C)",
            "class User(val context: android.content.Context)",
            "fun read() = android.os.SystemClock.elapsedRealtime()",
            "import com.google.firebase.Firebase as Telemetry",
        )) assertFinding(body, "forbidden API")
    }

    @Test
    fun `comments strings and Android compiled inputs are permitted`() {
        val source = "// android.content.Context\nval text = \"android.content.Context\""
        assertEquals(0, lint(source).size)
        assertEquals(0, lint("import android.content.Context", "androidProductionFiles").size)
    }

    @Test
    fun `Apple APIs belong to Apple source sets`() {
        assertFinding("class C(val url: platform.Foundation.NSURL)", "forbidden API")
        assertEquals(0, lint("class C(val url: platform.Foundation.NSURL)", "nativeProductionFiles").size)
    }

    @Test
    fun `portable tests cannot import JVM test frameworks or disable cases`() {
        for (source in listOf("import org.junit.Test as TestCase", "import io.mockk.mockk", "@kotlin.test.Ignore class C")) {
            assertTrue(lint(source, "sharedTestFiles").isNotEmpty(), source)
        }
    }

    @Test
    fun `State primary constructor cannot expose mutable properties or alias collections`() {
        assertFinding("data class State(var title: String)", "constructor property")
        assertFinding("typealias Bag = MutableList<String>\ndata class State(val rows: Bag)", "mutable collection")
        assertFinding("data class State(val rows: kotlin.collections.MutableMap<String, String>)", "mutable collection")
    }

    @Test
    fun `State and domain payloads cannot retain imported resource aliases`() {
        assertFinding("import org.jetbrains.compose.resources.StringResource as Copy\ndata class State(val label: Copy)", "Payload")
        assertFinding("typealias Copy = org.jetbrains.compose.resources.StringResource\ndata class State(val label: Copy)", "Payload")
        assertFinding("class UseCase(val resources: ResourceWrapper)", "Payload", pkg = "domain.usecase")
    }

    @Test
    fun `equivalent immutable payload shape and private field ordering pass`() {
        assertEquals(0, lint("data class State(val rows: ImmutableList<String>, val title: String)").size)
        assertEquals(0, lint("data class State(val heading: String, val rows: ImmutableList<String>)").size)
    }

    @Test
    fun `direct resource work and renamed resolver arguments in State updates fail`() {
        for (body in listOf("getString(Res.string.label)", "copy(title = resolver.getString(key))", "calculate(resolver)")) {
            assertFinding("class Handler(val resolver: StringResolver) { fun load() { updateState { $body } } }", "before the State update")
        }
    }

    @Test
    fun `transitive local resource helper inside State update fails`() {
        assertFinding(
            """
            class Handler(val resolver: StringResolver) {
                fun text() = resolver.getString(key)
                fun indirect() = text()
                fun load() { updateState { copy(title = indirect()) } }
            }
            """.trimIndent(),
            "before the State update",
        )
    }

    @Test
    fun `resource calculation before State and pure helper extraction pass`() {
        val source = """
            class Handler(val resolver: StringResolver) {
                fun text() = resolver.getString(key)
                fun pure(value: String) = value.trim()
                fun load() { val title = text(); updateState { copy(title = pure(title)) } }
            }
        """.trimIndent()
        assertEquals(0, lint(source).size)
    }

    @Test
    fun `file and declaration suppressions cannot bypass required contracts`() {
        assertFinding("@file:Suppress(\"KmpSharedContractsRule\")\n\nimport android.content.Context", "forbidden API")
        assertFinding("@Suppress(\"KmpSharedContractsRule\") data class State(var name: String)", "constructor property")
    }

    @Test
    fun `feature lookup aliases fail without banning literal documentation`() {
        assertFinding("import androidx.compose.runtime.compositionLocalOf as slot\nval value = slot { null }", "forbidden API")
        assertEquals(0, lint("val text = \"CompositionLocal ServiceLocator FactoryRegistry\"").size)
    }

    @Test
    fun `named State lambdas and nested resource type aliases cannot bypass checks`() {
        assertFinding(
            "class Handler(val r: StringResolver) { fun load() { updateState(update = { copy(title = r.getString(key)) }) } }",
            "before the State update",
        )
        assertFinding("typealias Labels = List<org.jetbrains.compose.resources.StringResource>\ndata class State(val labels: Labels)", "Payload")
        assertFinding("typealias Rows = List<MutableList<String>>\ndata class State(val rows: Rows)", "mutable collection")
    }

    @Test
    fun `pure overload and shadowed local values do not become resource helpers`() {
        assertEquals(0, lint(
            """
            class Handler(val resolver: StringResolver) {
                fun text() = resolver.getString(key)
                fun text(value: String) = value.trim()
                fun load() { updateState { copy(title = text("ok")) } }
                fun other() { val resolver: String = "ready"; updateState { copy(title = resolver) } }
            }
            """.trimIndent(),
        ).size)
    }

    @Test
    fun `root star imports remain forbidden`() {
        for (root in listOf("androidx.annotation", "androidx.compose.ui.res", "androidx.activity")) {
            assertFinding("import $root.*", "forbidden API")
        }
    }

    @Test
    fun `wrapper carrier cannot resolve copy through qualified or aliased receivers`() {
        for (expression in listOf("this.resourceWrapper.getString(key)", "w.getString(key)")) {
            val body = """
                package io.github.stslex.workeeper.feature.home.mvi.handler
                class ClickHandler(val resourceWrapper: ResourceWrapper) {
                    val w = resourceWrapper
                    fun text() = $expression
                }
            """.trimIndent()
            val findings = KmpSharedContractsRule(TestConfig("commonProductionFiles" to listOf(FIXTURE)))
                .lint(compileContentForTest(body, FIXTURE))
            assertTrue(findings.any { "outside its reviewed date reader" in it.message }, expression)
        }
    }

    @Test
    fun `cross file aliases cannot hide nested resource or mutable payloads`() {
        val alias = fixture.resolve("Types.kt").apply {
            writeText("package types; import org.jetbrains.compose.resources.StringResource as Copy; typealias Labels = List<Copy>")
        }
        val state = fixture.resolve("State.kt").apply {
            writeText("package $PACKAGE.mvi; import types.Labels as Headings; data class State(val headings: Headings)")
        }
        val files = listOf(alias.absolutePath, state.absolutePath)
        val rule = KmpSharedContractsRule(TestConfig("commonProductionFiles" to files))
        assertTrue(rule.lint(compileContentForTest(state.readText(), state.absolutePath))
            .any { "Payload" in it.message })
    }

    @Test
    fun `State mutator cannot reach an unreviewed mapper in another file`() {
        val pkg = "io.github.stslex.workeeper.feature.live_workout.mvi.mapper"
        val other = fixture.resolve("Other.kt").apply {
            writeText("package $pkg; object CopyMapper { fun text() = getString(Res.string.label) }")
        }
        val mutator = fixture.resolve("Mutator.kt").apply {
            writeText("package $pkg; object LiveSetMutator { fun mutate() = CopyMapper.text() }")
        }
        val rule = KmpSharedContractsRule(TestConfig("commonProductionFiles" to listOf(other.absolutePath, mutator.absolutePath)))
        assertTrue(rule.lint(compileContentForTest(mutator.readText(), mutator.absolutePath))
            .any { "State mutators" in it.message })
    }

    @Test
    fun `Settings availability cannot acquire an implicit constructor or factory default`() {
        for ((constructor, factory) in listOf(" = false" to "", "" to " = false")) {
            val source = """
                package io.github.stslex.workeeper.feature.settings.mvi.store
                interface SettingsStore {
                    data class State(val backupAvailable: Boolean$constructor) {
                        companion object {
                            fun initial(backupAvailable: Boolean$factory) = State(backupAvailable)
                        }
                    }
                }
            """.trimIndent()
            assertTrue(settingsFindings(source).any { "explicit required" in it.message }, source)
        }
    }

    @Test
    fun `Settings authentication carriers remain public opaque markers`() {
        for (marker in listOf("SettingsAuthRequest", "SettingsAuthResult")) {
            for (declaration in listOf(
                "interface $marker { val raw: Payload get() = Unit }",
                "interface $marker { fun unwrap(): Payload }",
                "internal interface $marker",
                "class $marker",
            )) {
                val source = """
                    package io.github.stslex.workeeper.feature.settings.mvi.model
                    typealias Payload = Any
                    $declaration
                """.trimIndent()
                assertTrue(settingsFindings(source).any { "opaque marker" in it.message }, source)
            }
        }
    }

    @Test
    fun `Settings required availability and empty marker formatting are equivalent`() {
        assertEquals(0, settingsFindings("""
            package io.github.stslex.workeeper.feature.settings.mvi.store
            typealias Available = Boolean
            interface SettingsStore {
                data class State(val backupAvailable: Available) {
                    companion object {
                        fun initial(backupAvailable: Available) = State(backupAvailable)
                    }
                }
            }
        """.trimIndent()).size)
        assertEquals(0, settingsFindings("""
            package io.github.stslex.workeeper.feature.settings.mvi.model
            public interface SettingsAuthRequest { }
            interface SettingsAuthResult
        """.trimIndent()).size)
    }

    @Test
    fun `feature copy reads cannot bypass the injected resolver outside State updates`() {
        for ((imports, call) in listOf(
            "" to "org.jetbrains.compose.resources.getString(key)",
            "import org.jetbrains.compose.resources.getString as readCopy" to "readCopy(key)",
            "import org.jetbrains.compose.resources.getPluralString as readCount" to "readCount(key, 2)",
            "import org.jetbrains.compose.resources.stringResource as composeCopy" to "composeCopy(key)",
        )) {
            val source = """
                package io.github.stslex.workeeper.feature.home.mvi.handler
                $imports
                class CommonHandler { suspend fun text() = $call }
            """.trimIndent()
            assertTrue(settingsFindings(source).any { "StringResolver" in it.message }, source)
        }
    }

    @Test
    fun `feature UI cannot resolve suspended copy and resolver helper extraction stays valid`() {
        assertTrue(settingsFindings("""
            package io.github.stslex.workeeper.feature.home.ui
            class Presenter(val resolver: StringResolver) { suspend fun text() = resolver.getString(key) }
        """.trimIndent()).any { "composition" in it.message })
        assertEquals(0, settingsFindings("""
            package io.github.stslex.workeeper.feature.home.mvi.handler
            class Handler(val copy: StringResolver) {
                suspend fun label() = copy.getString(key)
                suspend fun load() { val title = label(); updateState { copy(title = title) } }
            }
        """.trimIndent()).size)
        assertEquals(0, settingsFindings("""
            package io.github.stslex.workeeper.feature.home.ui
            import androidx.compose.runtime.Composable as Ui
            import org.jetbrains.compose.resources.stringResource as label
            @Ui fun Screen() { val title = label(key) }
        """.trimIndent()).size)
    }

    @Test
    fun `qualified CMP read remains owned when its result is chained`() {
        assertTrue(settingsFindings("""
            package io.github.stslex.workeeper.feature.home.mvi.handler
            class Handler { suspend fun text() = org.jetbrains.compose.resources.getString(key).trim() }
        """.trimIndent()).any { "StringResolver" in it.message })
    }

    @Test
    fun `CMP star imports cannot bypass copy ownership`() {
        for (call in listOf("getString(key)", "getPluralString(key, 2)", "stringResource(key)", "pluralStringResource(key, 2)")) {
            assertTrue(settingsFindings("""
                package io.github.stslex.workeeper.feature.home.mvi.handler
                import org.jetbrains.compose.resources.*
                class Handler { suspend fun text() = $call }
            """.trimIndent()).any { "StringResolver" in it.message }, call)
        }
    }

    @Test
    fun `UI suspended copy remains owned through implicit scope receivers`() {
        for (call in listOf("resolver.run { getString(key) }", "with(resolver) { getString(key) }", "resolver.apply { getString(key) }")) {
            assertTrue(settingsFindings("""
                package io.github.stslex.workeeper.feature.home.ui
                class Presenter(val resolver: StringResolver) { suspend fun text() = $call }
            """.trimIndent()).any { "composition" in it.message }, call)
        }
    }

    @Test
    fun `Settings authentication aliases cannot expose payloads`() {
        for (marker in listOf("SettingsAuthRequest", "SettingsAuthResult")) {
            assertTrue(settingsFindings("""
                package io.github.stslex.workeeper.feature.settings.mvi.model
                interface ExposedAuthPayload { val raw: Any get() = Unit }
                typealias $marker = ExposedAuthPayload
            """.trimIndent()).any { "opaque marker" in it.message }, marker)
        }
    }

    @Test
    fun `equivalent copy bindings and opaque marker aliases remain valid`() {
        for (source in listOf(
            """
                package io.github.stslex.workeeper.feature.home.ui
                fun screen() = org.jetbrains.compose.resources.stringResource(key).trim()
            """,
            """
                package io.github.stslex.workeeper.feature.home.mvi.handler
                import org.jetbrains.compose.resources.*
                fun getString(key: Key) = "local helper"
                class Handler { fun text() = getString(key) }
            """,
            """
                package io.github.stslex.workeeper.feature.home.mvi.handler
                class Handler(val resolver: StringResolver) {
                    suspend fun text() = resolver.run { getString(key) }
                }
            """,
            """
                package io.github.stslex.workeeper.feature.settings.mvi.model
                interface OpaqueRequest
                interface OpaqueResult
                typealias SettingsAuthRequest = OpaqueRequest
                typealias SettingsAuthResult = OpaqueResult
            """,
        )) assertEquals(0, settingsFindings(source.trimIndent()).size, source)
    }

    @Test
    fun `a helper in an unrelated scope cannot hide a CMP star call`() {
        assertTrue(settingsFindings("""
            package io.github.stslex.workeeper.feature.home.mvi.handler
            import org.jetbrains.compose.resources.*
            fun unrelated() { fun getString(key: StringResource) = "local" }
            suspend fun text(key: StringResource) = getString(key)
        """.trimIndent()).any { "StringResolver" in it.message })
    }

    @Test
    fun `nested scope receivers cannot hide suspended UI copy`() {
        for (call in listOf(
            "resolver.run { \"scope\".run { getString(key) } }",
            "with(resolver) { with(\"scope\") { getString(key) } }",
            "resolver.apply { \"scope\".apply { getString(key) } }",
        )) assertTrue(settingsFindings("""
            package io.github.stslex.workeeper.feature.home.ui
            class Presenter(val resolver: StringResolver) { suspend fun text() = $call }
        """.trimIndent()).any { "composition" in it.message }, call)
    }

    @Test
    fun `a canonical mutator alias cannot hide an impure implementation`() {
        val source = """
            package io.github.stslex.workeeper.feature.live_workout.mvi.mapper
            typealias LiveSetMutator = RenamedMutator
            class RenamedMutator(val resolver: StringResolver) {
                fun mutate() = resolver.getString(key)
            }
        """.trimIndent()
        assertTrue(settingsFindings(source).any { "State mutators" in it.message })
    }

    @Test
    fun `pure mutator aliases retain both implementation and mapper permissions`() {
        val source = """
            package io.github.stslex.workeeper.feature.live_workout.mvi.mapper
            typealias LiveSetMutator = RenamedMutator
            typealias StateStatusMapper = RenamedStatus
            class RenamedMutator { fun mutate(value: String) = StateStatusMapper.map(value) }
            class RenamedStatus { companion object { fun map(value: String) = value.trim() } }
        """.trimIndent()
        assertEquals(0, settingsFindings(source).size)
    }

    @Test
    fun `required mutator ownership cannot disappear with its declaration`() {
        val source = "package io.github.stslex.workeeper.feature.live_workout.mvi.mapper; object Remaining"
        val findings = KmpSharedContractsRule(TestConfig(
            "commonProductionFiles" to listOf(FIXTURE),
            "requirePureMutatorInventory" to true,
        )).lint(compileContentForTest(source, FIXTURE))
        assertEquals(6, findings.count { "Missing reviewed State mutator" in it.message })
    }

    @Test
    fun `same package mutator alias cannot hide an impure target in another file`() {
        assertTrue(mutatorAliasFindings("class RenamedMutator(val resolver: StringResolver)")
            .any { "State mutators" in it.message })
    }

    @Test
    fun `same package pure alias target in another file satisfies the inventory`() {
        assertEquals(0, mutatorAliasFindings("class RenamedMutator { fun mutate(value: String) = value.trim() }").size)
    }

    private fun mutatorAliasFindings(target: String): List<io.gitlab.arturbosch.detekt.api.Finding> {
        val pkg = "io.github.stslex.workeeper.feature.live_workout"
        val alias = fixture.resolve("Alias.kt").apply {
            writeText("package $pkg.mvi.mapper; typealias LiveSetMutator = RenamedMutator")
        }
        val implementation = fixture.resolve("Implementation.kt").apply { writeText("package $pkg.mvi.mapper; $target") }
        val others = fixture.resolve("OtherMutators.kt").apply {
            writeText("package $pkg.mvi.mapper; class StateStatusMapper; object LiveWorkoutMapper; " +
                "object LiveSetRowsResolver; object ExerciseDoneRule")
        }
        val domain = fixture.resolve("Domain.kt").apply { writeText("package $pkg.domain.mapper; object LiveWorkoutDomainMapper") }
        val rule = KmpSharedContractsRule(TestConfig(
            "commonProductionFiles" to listOf(alias, implementation, others, domain).map { it.absolutePath },
            "requirePureMutatorInventory" to true,
        ))
        return rule.lint(compileContentForTest(implementation.readText(), implementation.absolutePath))
    }

    private fun settingsFindings(source: String) =
        KmpSharedContractsRule(TestConfig("commonProductionFiles" to listOf(FIXTURE)))
            .lint(compileContentForTest(source, FIXTURE))

    private fun assertFinding(source: String, diagnostic: String, pkg: String = "mvi") {
        val findings = lint(source, pkg = pkg)
        assertTrue(findings.any { diagnostic in it.message }, "$source: ${findings.map { it.message }}")
    }

    private fun lint(source: String, scope: String = "commonProductionFiles", pkg: String = "mvi") =
        KmpSharedContractsRule(TestConfig(scope to listOf(FIXTURE))).lint(
            compileContentForTest(
                if (source.startsWith("@file:")) source.replace("\n\n", "\npackage $PACKAGE.$pkg\n")
                else "package $PACKAGE.$pkg\n$source",
                FIXTURE,
            ),
        )

    private companion object {
        const val FIXTURE = "/project/configured/shared/Fixture.kt"
        const val PACKAGE = "io.github.stslex.workeeper.feature.sample"
    }
}
