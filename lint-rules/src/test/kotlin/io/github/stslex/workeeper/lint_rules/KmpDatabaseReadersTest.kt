package io.github.stslex.workeeper.lint_rules

import io.github.detekt.test.utils.compileContentForTest
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class KmpDatabaseReadersTest {
    @TempDir
    lateinit var fixture: File

    @Test
    fun `unreviewed production mapper rejects imports aliases and qualified types`() {
        for (body in listOf(
            "import $DATABASE.sets.SetTypeDataModel\nclass ExtraMapper(val value: SetTypeDataModel)",
            "import $DATABASE.sets.SetTypeDataModel as Kind\nclass ExtraMapper(val value: Kind)",
            "class ExtraMapper(val value: $DATABASE.sets.SetTypeDataModel)",
        )) assertRejected("package $LIVE.domain.mapper\n$body")
    }

    @Test
    fun `host test readers are restricted in both evaluated modules`() {
        assertRejected("package $LIVE.mvi.mapper\nclass ExtraTest { val rows = $DATABASE.testfixtures.WorkoutTargetParityFixture.SCENARIOS }")
        assertRejected(
            "package $CHART.domain\nclass ExtraTest { val rows = $DATABASE.testfixtures.PrRuleFixture.SCENARIOS }",
            "feature:exercise-chart",
        )
    }

    @Test
    fun `reviewed test owners cannot read other database types`() {
        assertRejected("package $LIVE.domain\nclass LiveWorkoutInteractorImplTest(val wrong: $DATABASE.AppDatabase)")
        assertRejected("package $LIVE.mvi.mapper\nclass ExerciseDoneRuleTest(val wrong: $DATABASE.sets.PlanSetDataModel)")
        assertRejected(
            "package $CHART.domain\nclass ChartFolderPrRuleParityTest(val wrong: $DATABASE.sets.SetTypeDataModel)",
            "feature:exercise-chart",
        )
    }

    @Test
    fun `reviewed mapper and nested test readers retain their permissions`() {
        val sources = listOf(
            "package $LIVE.domain.mapper\nobject LiveWorkoutDomainMapper { fun map(value: $DATABASE.sets.SetTypeDataModel) = value }",
            "package $LIVE.domain\nclass LiveWorkoutInteractorImplTest { class Fake(val rows: List<$DATABASE.sets.PlanSetDataModel>) }",
            "package $LIVE.mvi.mapper\nclass ExerciseDoneRuleTest { val rows = $DATABASE.testfixtures.WorkoutTargetParityFixture.SCENARIOS }",
        )
        sources.forEach { assertEquals(0, findings(it).size) }
        assertEquals(0, findings(
            "package $CHART.domain\nclass ChartFolderPrRuleParityTest { val rows = $DATABASE.testfixtures.PrRuleFixture.SCENARIOS }",
            "feature:exercise-chart",
        ).size)
    }

    @Test
    fun `a canonical reader alias can target a renamed class in another configured file`() {
        val source = "package $LIVE.domain\nclass RenamedSuite { class Fake(val row: $DATABASE.sets.PlanSetDataModel) }"
        val alias = "package $LIVE.domain\ntypealias LiveWorkoutInteractorImplTest = Intermediate\ntypealias Intermediate = RenamedSuite"
        assertEquals(0, findings(source, companions = listOf(alias)).size)
    }

    @Test
    fun `a reviewed declaration does not grant a sibling class its reader permission`() {
        assertRejected("package $LIVE.domain\nclass LiveWorkoutInteractorImplTest\nclass Extra(val row: $DATABASE.sets.PlanSetDataModel)")
    }

    @Test
    fun `database aliases resolve across files before checking their consumer`() {
        val alias = "package $LIVE.domain\ntypealias Row = $DATABASE.sets.PlanSetDataModel"
        assertTrue(findings("package $LIVE.domain\nclass Extra(val row: Row)", companions = listOf(alias))
            .any { "reviewed reader" in it.message })
        assertEquals(0, findings("package $LIVE.domain\nclass LiveWorkoutInteractorImplTest(val row: Row)", companions = listOf(alias)).size)
    }

    @Test
    fun `database star imports cannot hide an unreviewed reader even when suppressed`() {
        val source = "@file:Suppress(\"KmpSharedContractsRule\")\npackage $LIVE.domain\nimport $DATABASE.sets.*\nclass Extra(val row: PlanSetDataModel)"
        assertTrue(findings(source).any { "reviewed reader" in it.message })
    }

    @Test
    fun `reader checking does not classify host Android APIs comments or strings as shared code`() {
        assertEquals(0, findings("package $LIVE.domain\nimport android.content.Context\nclass Host(val context: Context) { val note = \"$DATABASE.AppDatabase\" }").size)
        assertEquals(0, findings("package $LIVE.domain\n// $DATABASE.AppDatabase\nclass Host").size)
    }

    @Test
    fun `reviewed fixture permits a package star import and nested members`() {
        val source = """
            package $CHART.domain
            import $DATABASE.testfixtures.*
            class ChartFolderPrRuleParityTest { val rows = PrRuleFixture.SCENARIOS }
        """.trimIndent()
        assertEquals(0, findings(source, "feature:exercise-chart").size)
    }

    @Test
    fun `local types and helper calls take precedence over database star imports`() {
        val source = """
            package $LIVE.domain
            import $DATABASE.testfixtures.*
            class PrRuleFixture
            class Host {
                fun databaseHelper() = "local"
                val row = PrRuleFixture()
                val value = databaseHelper()
            }
        """.trimIndent()
        assertEquals(0, findings(source).size)
        val unreviewed = """
            package $LIVE.domain
            import $DATABASE.testfixtures.*
            class Extra { val value = databaseHelper() }
        """.trimIndent()
        assertTrue(findings(unreviewed).any { "reviewed reader" in it.message })
    }

    @Test
    fun `later local values cannot hide an earlier database call`() {
        val source = """
            package $LIVE.domain
            import $DATABASE.testfixtures.*
            class Extra {
                fun value(): Int {
                    val first = databaseHelper()
                    val databaseHelper = { 2 }
                    return first + databaseHelper()
                }
            }
        """.trimIndent()
        assertRejected(source)
        assertEquals(0, findings(source.replace("val first = databaseHelper()", "val first = 1")).size)
    }

    @Test
    fun `lambda parameters cannot hide database calls outside their lambda`() {
        val source = """
            package $LIVE.domain
            import $DATABASE.testfixtures.*
            class Extra {
                fun value(): Int {
                    val local = { databaseHelper: () -> Int -> databaseHelper() }
                    return databaseHelper() + local { 2 }
                }
            }
        """.trimIndent()
        assertRejected(source)
        assertEquals(0, findings(source.replace("return databaseHelper() +", "return 1 +")).size)
    }

    @Test
    fun `an unrelated nested type cannot hide a database fixture`() {
        val source = """
            package $LIVE.domain
            import $DATABASE.testfixtures.*
            class Unrelated { class PrRuleFixture }
            class Extra { val rows = PrRuleFixture.SCENARIOS }
        """.trimIndent()
        assertRejected(source)
    }

    @Test
    fun `ambiguous database star candidates require explicit resolution`() {
        val source = """
            package $LIVE.domain
            import $DATABASE.testfixtures.*
            import $DATABASE.sets.*
            class Extra { val value = databaseHelper("value") }
        """.trimIndent()
        assertTrue(findings(source).any { "Ambiguous database" in it.message })
    }

    @Test
    fun `external database aliases retain a target from another symbol file`() {
        assertRejected("package $LIVE.domain\nimport $DATABASE.sets.RowAlias\nclass Extra(val row: RowAlias)")
        assertEquals(0, findings(
            "package $LIVE.domain\nimport $DATABASE.sets.RowAlias\nclass LiveWorkoutInteractorImplTest(val row: RowAlias)",
        ).size)
    }

    @Test
    fun `same package symbols in another file precede database star imports`() {
        val source = """
            package $LIVE.domain
            import $DATABASE.testfixtures.*
            class Extra { val rows = PrRuleFixture.SCENARIOS; val value = databaseHelper() }
        """.trimIndent()
        val local = "package $LIVE.domain; object PrRuleFixture { val SCENARIOS = 1 }; fun databaseHelper() = 2"
        assertEquals(0, findings(source, companions = listOf(local)).size)
        for (declaration in listOf(
            "private fun databaseHelper() = 2",
            "private object PrRuleFixture { val SCENARIOS = 1 }",
            "private typealias PrRuleFixture = LocalFixture; object LocalFixture { val SCENARIOS = 1 }",
        )) {
            val reference = if ("databaseHelper" in declaration) "databaseHelper()" else "PrRuleFixture.SCENARIOS"
            val consumer = "package $LIVE.domain; import $DATABASE.testfixtures.*; class Extra { val value = $reference }"
            assertTrue(findings(consumer, companions = listOf("package $LIVE.domain; $declaration"))
                .any { "reviewed reader" in it.message }, declaration)
            assertEquals(0, findings("$consumer\n$declaration").size, declaration)
        }
        val approved = """
            package $CHART.domain
            import $DATABASE.testfixtures.*
            import $DATABASE.sets.*
            class ChartFolderPrRuleParityTest { val value = PrRuleFixture.SCENARIOS }
        """.trimIndent()
        assertEquals(0, findings(approved, "feature:exercise-chart").size)
    }

    private fun assertRejected(source: String, module: String = "feature:live-workout") {
        assertTrue(findings(source, module).any { "reviewed reader" in it.message }, source)
    }

    private fun findings(source: String, module: String = "feature:live-workout", companions: List<String> = emptyList())
        : List<io.gitlab.arturbosch.detekt.api.Finding> {
        val target = fixture.resolve("custom/host/Renamed.kt").apply { parentFile.mkdirs(); writeText(source) }
        val others = companions.mapIndexed { index, content ->
            fixture.resolve("custom/alias$index.kt").apply { parentFile.mkdirs(); writeText(content) }
        }
        val symbols = listOf(
            "package $DATABASE; class AppDatabase",
            "package $DATABASE.sets; typealias RowAlias = PlanSetDataModel",
            "package $DATABASE.sets; class PlanSetDataModel; class SetTypeDataModel; fun databaseHelper(value: String) = 1; " +
                "private object PrRuleFixture { val SCENARIOS = 0 }",
            "package $DATABASE.testfixtures; object PrRuleFixture { val SCENARIOS = 1 }; " +
                "object WorkoutTargetParityFixture { val SCENARIOS = 1 }; fun databaseHelper() = 1",
        ).mapIndexed { index, content ->
            fixture.resolve("symbols$index.kt").apply { writeText(content) }
        }
        return KmpSharedContractsRule(TestConfig(
            "modulePath" to module,
            "databaseSymbolFiles" to symbols.map { it.absolutePath },
            "allFiles" to (others + target).map { it.absolutePath },
        )).lint(compileContentForTest(source, target.absolutePath))
    }

    private companion object {
        const val DATABASE = "io.github.stslex.workeeper.core.data.database"
        const val LIVE = "io.github.stslex.workeeper.feature.live_workout"
        const val CHART = "io.github.stslex.workeeper.feature.exercise_chart"
    }
}
