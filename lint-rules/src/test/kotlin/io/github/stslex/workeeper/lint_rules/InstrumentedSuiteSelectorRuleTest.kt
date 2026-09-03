// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for [InstrumentedSuiteSelectorRule]. The positive controls are load-bearing: they prove
 * a green run means "every test is selectable", not "the rule never fired".
 */
internal class InstrumentedSuiteSelectorRuleTest {

    private val rule = InstrumentedSuiteSelectorRule()

    private val smokeImport = "import io.github.stslex.workeeper.core.ui.test.annotations.Smoke"
    private val regressionImport =
        "import io.github.stslex.workeeper.core.ui.test.annotations.Regression"

    // Positive controls (must fire).

    @Test
    fun `flags a test class carrying no suite annotation`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            import org.junit.Test

            class ExampleScreenTest {

                @Test
                fun rendersTitle() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "Unselectable test must be flagged, got: $findings")
        assertTrue(findings.single().message.contains("rendersTitle"))
    }

    @Test
    fun `flags every unannotated test in the class, not just the first`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            import org.junit.Test

            class ExampleScreenTest {

                @Test
                fun first() = Unit

                @Test
                fun second() = Unit

                @Test
                fun third() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(3, findings.size, "Each unselectable test must be flagged, got: $findings")
    }

    @Test
    fun `flags a locally declared Smoke that shadows the canonical one`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            import org.junit.Test

            annotation class Smoke

            @Smoke
            class ExampleScreenTest {

                @Test
                fun rendersTitle() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(
            1,
            findings.size,
            "A same-named annotation from another package satisfies no instrumentation " +
                "filter and must not credit coverage, got: $findings",
        )
    }

    @Test
    fun `flags a suite annotation used without the canonical import`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            import org.junit.Test

            @Regression
            class ExampleScreenTest {

                @Test
                fun rendersTitle() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "Unresolvable @Regression must be flagged, got: $findings")
    }

    @Test
    fun `flags the unannotated sibling when only one test carries the annotation`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            $regressionImport
            import org.junit.Test

            class ExampleScreenTest {

                @Regression
                @Test
                fun selected() = Unit

                @Test
                fun unselected() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "Only the bare test must be flagged, got: $findings")
        assertTrue(findings.single().message.contains("unselected"))
    }

    @Test
    fun `flags an unannotated test whose JUnit import is aliased`() {
        // The dangerous direction: an alias hides the test, so it passes both gate halves.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            import org.junit.Test as InstrumentedTest

            class ExampleScreenTest {

                @InstrumentedTest
                fun rendersTitle() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "An aliased @Test is still a test, got: $findings")
        assertTrue(findings.single().message.contains("rendersTitle"))
    }

    @Test
    fun `flags an unannotated test whose JUnit import is a wildcard`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            import org.junit.*

            class ExampleScreenTest {

                @Test
                fun rendersTitle() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "A star-imported @Test is still a test, got: $findings")
    }

    @Test
    fun `accepts an aliased JUnit test that does carry a suite annotation`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            $smokeImport
            import org.junit.Test as InstrumentedTest

            @Smoke
            class ExampleScreenTest {

                @InstrumentedTest
                fun rendersTitle() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "Alias detection must not create false positives, got: $findings")
    }

    @Test
    fun `flags an unannotated test written with a backticked annotation name`() {
        // `shortName` normalises the escaped identifier; the raw `typeReference.text` does not.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            import org.junit.Test

            class ExampleScreenTest {

                @`Test`
                fun rendersTitle() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "A backticked @`Test` is still a test, got: $findings")
    }

    @Test
    fun `accepts a backticked suite annotation`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            $smokeImport
            import org.junit.Test

            @`Smoke`
            class ExampleScreenTest {

                @Test
                fun rendersTitle() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "A backticked @`Smoke` still selects, got: $findings")
    }

    @Test
    fun `flags a test whose only suite annotation sits on an abstract base class`() {
        // `@Smoke` is not `@Inherited`, and androidx.test reads the concrete class's annotations.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            $smokeImport
            import org.junit.Test

            @Smoke
            abstract class BaseScreenTest {

                @Test
                fun sharedCase() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(
            1,
            findings.size,
            "A suite annotation on an abstract class does not select at runtime, got: $findings",
        )
    }

    @Test
    fun `flags a test declared in an open class even when the class is annotated`() {
        // A concrete subclass declaring no @Test is never visited, so the shape itself is refused.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            $smokeImport
            import org.junit.Test

            @Smoke
            open class ReusableScreenTest {

                @Test
                fun sharedCase() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "An inheritable test class is refused, got: $findings")
        assertTrue(findings.single().message.contains("concrete"))
    }

    @Test
    fun `flags an unannotated test in a nested class`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            $smokeImport
            import org.junit.Test

            @Smoke
            class OuterTest {

                @Test
                fun outerCase() = Unit

                // The outer class's annotation does not propagate to a nested class: the
                // instrumentation filter reads the annotations of the test's OWN declaring
                // class, so this test really is unselectable.
                class InnerTest {

                    @Test
                    fun innerCase() = Unit
                }
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "Nested unselectable test must be flagged, got: $findings")
        assertTrue(findings.single().message.contains("innerCase"))
    }

    // Negative controls (must stay silent).

    @Test
    fun `accepts a class level Smoke`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            $smokeImport
            import org.junit.Test

            @Smoke
            class ExampleScreenTest {

                @Test
                fun rendersTitle() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "Class-level @Smoke covers its tests, got: $findings")
    }

    @Test
    fun `accepts a method level Regression alongside a class level Smoke`() {
        // The real shape of AllExercisesScreenTest: class-level @Smoke plus one method @Regression.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            $smokeImport
            $regressionImport
            import org.junit.Test

            @Smoke
            class ExampleScreenTest {

                @Test
                fun plainSmokeCase() = Unit

                @Regression
                @Test
                fun alsoRegressionCase() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "Mixed-level annotation is valid, got: $findings")
    }

    @Test
    fun `accepts an aliased import`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            import io.github.stslex.workeeper.core.ui.test.annotations.Smoke as SmokeSuite
            import org.junit.Test

            @SmokeSuite
            class ExampleScreenTest {

                @Test
                fun rendersTitle() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "An alias still resolves to the real FQN, got: $findings")
    }

    @Test
    fun `accepts a wildcard import of the annotations package`() {
        // Load-bearing: wildcard imports are permitted in androidTest, exactly where this polices.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            import io.github.stslex.workeeper.core.ui.test.annotations.*
            import org.junit.Test

            @Smoke
            class ExampleScreenTest {

                @Test
                fun rendersTitle() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "A wildcard import binds the suite names, got: $findings")
    }

    @Test
    fun `flags a wildcard import of some other package`() {
        // The wildcard branch must not degrade into "any star import credits coverage".
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            import io.github.stslex.workeeper.core.ui.test.*
            import org.junit.Test

            @Smoke
            class ExampleScreenTest {

                @Test
                fun rendersTitle() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(
            1,
            findings.size,
            "A wildcard over a DIFFERENT package binds nothing, got: $findings",
        )
    }

    @Test
    fun `accepts a fully qualified annotation with no import`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            import org.junit.Test

            @io.github.stslex.workeeper.core.ui.test.annotations.Regression
            class ExampleScreenTest {

                @Test
                fun rendersTitle() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "FQN use needs no import, got: $findings")
    }

    @Test
    fun `accepts an ignored test that still carries a suite annotation`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            $smokeImport
            import org.junit.Ignore
            import org.junit.Test

            @Smoke
            class ExampleScreenTest {

                @Ignore("Awaiting feature rewrite")
                @Test
                fun pendingCase() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "@Ignore does not remove coverage, got: $findings")
    }

    @Test
    fun `ignores a class with no tests at all`() {
        // Harness classes under src/androidTest declare no @Test and are selected by nothing.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.harness

            class NavPaths {

                fun openTraining(name: String) = Unit
            }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "A non-test class needs no annotation, got: $findings")
    }
}
