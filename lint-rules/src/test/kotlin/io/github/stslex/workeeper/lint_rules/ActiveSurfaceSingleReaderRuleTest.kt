// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.github.detekt.test.utils.compileContentForTest
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The rule keys on the file's path, so every fixture is compiled *at a path*.
 *
 * `Rule.lint(String)` synthesises a virtual file at an internal location, which no path predicate
 * can match — a test written that way would pass for the wrong reason, reporting a violation
 * because the fixture is nowhere rather than because the call is misplaced. `lintAt` below uses the
 * same `compileContentForTest(content, path)` route `DomainLayerPurityRuleTest` established.
 */
internal class ActiveSurfaceSingleReaderRuleTest {

    private val rule = ActiveSurfaceSingleReaderRule()

    // ---- known-NEGATIVE anchors: each one compiles and looks reasonable, and MUST flag ----

    @Test
    fun `a second call site in another feature is flagged`() {
        val findings = rule.lintAt(
            path = "feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/ui/" +
                "components/ActiveSessionBanner.kt",
            content = """
            package io.github.stslex.workeeper.feature.home.ui.components

            import io.github.stslex.workeeper.core.ui.kit.components.surface.AppActiveSurface

            @Composable
            fun ActiveSessionBanner() {
                AppActiveSurface {
                    Text("Upper body")
                }
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size, "A second active surface must be flagged.")
        assertTrue(
            findings.first().message.lowercase().contains("exactly one element"),
            "The message must state the invariant, not merely that a rule fired.",
        )
    }

    @Test
    fun `a call site inside the kit but outside the surface package is flagged`() {
        val findings = rule.lintAt(
            path = "core/ui/kit/src/main/kotlin/io/github/stslex/workeeper/core/ui/kit/" +
                "components/card/AppCard.kt",
            content = """
            package io.github.stslex.workeeper.core.ui.kit.components.card

            @Composable
            fun AppCard() {
                AppActiveSurface { Text("nope") }
            }
            """.trimIndent(),
        )

        assertEquals(
            1,
            findings.size,
            "Living in the kit is not an exemption — only the surface package itself is.",
        )
    }

    @Test
    fun `a call site in a feature test is flagged`() {
        val findings = rule.lintAt(
            path = "feature/live-workout/src/test/kotlin/io/github/stslex/workeeper/feature/" +
                "live_workout/SomeTest.kt",
            content = """
            package io.github.stslex.workeeper.feature.live_workout

            @Composable
            fun Fixture() {
                AppActiveSurface { Text("second") }
            }
            """.trimIndent(),
        )

        assertEquals(
            1,
            findings.size,
            "The exemption is the kit's own goldens, not any test anywhere.",
        )
    }

    @Test
    fun `two call sites in one disallowed file are both flagged`() {
        val findings = rule.lintAt(
            path = "feature/archive/src/main/kotlin/io/github/stslex/workeeper/feature/archive/" +
                "ui/ArchiveScreen.kt",
            content = """
            package io.github.stslex.workeeper.feature.archive.ui

            @Composable
            fun ArchiveScreen() {
                AppActiveSurface { Text("one") }
                AppActiveSurface { Text("two") }
            }
            """.trimIndent(),
        )

        assertEquals(2, findings.size, "Each offending call site is reported.")
    }

    @Test
    fun `a second call inside the permitted reader is flagged`() {
        val findings = rule.lintAt(
            path = PERMITTED_READER,
            content = """
            package io.github.stslex.workeeper.feature.live_workout.ui.components

            @Composable
            fun LiveExerciseCard() {
                AppActiveSurface { Text("current") }
                AppActiveSurface { Text("also current") }
            }
            """.trimIndent(),
        )

        assertEquals(
            1,
            findings.size,
            "The first call is the permitted one; the second is a second raised surface.",
        )
        assertTrue(
            findings.first().message.contains("does not license"),
            "The message must say why being the right file is not enough.",
        )
    }

    @Test
    fun `a new file dropped into the surface package is flagged`() {
        val findings = rule.lintAt(
            path = "core/ui/kit/src/main/kotlin/io/github/stslex/workeeper/core/ui/kit/" +
                "components/surface/AnotherRaisedThing.kt",
            content = """
            package io.github.stslex.workeeper.core.ui.kit.components.surface

            @Composable
            fun AnotherRaisedThing() {
                AppActiveSurface { Text("smuggled in") }
            }
            """.trimIndent(),
        )

        assertEquals(
            1,
            findings.size,
            "The exemption is the declaring file itself, not its package — otherwise a new " +
                "file next to it is an unguarded way to raise a second surface.",
        )
    }

    // ---- known-POSITIVE anchors: the intended usage must not be flagged ----

    @Test
    fun `the permitted reader passes`() {
        val findings = rule.lintAt(
            path = PERMITTED_READER,
            content = """
            package io.github.stslex.workeeper.feature.live_workout.ui.components

            import io.github.stslex.workeeper.core.ui.kit.components.surface.AppActiveSurface

            @Composable
            fun LiveExerciseCard() {
                AppActiveSurface {
                    Text("Bench press")
                }
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "The active exercise is the one permitted reader.")
    }

    @Test
    fun `the declaring file may preview itself`() {
        val findings = rule.lintAt(
            path = "core/ui/kit/src/main/kotlin/io/github/stslex/workeeper/core/ui/kit/" +
                "components/surface/AppActiveSurface.kt",
            content = """
            package io.github.stslex.workeeper.core.ui.kit.components.surface

            @Composable
            fun AppActiveSurface(content: @Composable () -> Unit) {
                Box { content() }
            }

            @Composable
            private fun AppActiveSurfacePreview() {
                AppActiveSurface { Text("Bench press") }
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "A component previewing itself is not a second surface.")
    }

    @Test
    fun `the kit goldens may render it`() {
        val findings = rule.lintAt(
            path = "core/ui/kit/src/test/kotlin/io/github/stslex/workeeper/core/ui/kit/golden/" +
                "ActiveSurfaceGoldenTest.kt",
            content = """
            package io.github.stslex.workeeper.core.ui.kit.golden

            internal class ActiveSurfaceGoldenTest {
                fun activeSurface() {
                    AppActiveSurface { Text("Bench press") }
                }
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "Rendering it in a golden does not put it in front of a user.")
    }

    @Test
    fun `an unrelated composable whose name merely contains the token is not flagged`() {
        val findings = rule.lintAt(
            path = "feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/ui/Home.kt",
            content = """
            package io.github.stslex.workeeper.feature.home.ui

            @Composable
            fun Home() {
                AppActiveSurfaceHeader { Text("not the same composable") }
                RememberAppActiveSurface()
            }
            """.trimIndent(),
        )

        assertEquals(
            0,
            findings.size,
            "Matching must be on the whole callee name, not on a substring.",
        )
    }

    @Test
    fun `a file that never mentions it is not flagged`() {
        val findings = rule.lintAt(
            path = "feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/ui/Home.kt",
            content = """
            package io.github.stslex.workeeper.feature.home.ui

            @Composable
            fun Home() {
                AppCard { Text("ordinary") }
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "The rule must be silent on files it has no business in.")
    }

    private fun ActiveSurfaceSingleReaderRule.lintAt(
        path: String,
        content: String,
    ) = lint(compileContentForTest(content, path))

    private companion object {

        const val PERMITTED_READER =
            "feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/" +
                "live_workout/ui/components/LiveExerciseCard.kt"
    }
}
