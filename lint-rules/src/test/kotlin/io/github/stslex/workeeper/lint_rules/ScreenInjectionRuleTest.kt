// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Graph-extension arc coverage for [ScreenInjectionRule].
 *
 * Under shape B the route arg is a `@Provides` bound instance on the contributed extension factory, so it
 * is an ORDINARY binding in the feature scope and any `@Inject` node can resolve it — the mechanical
 * guarantee `@AssistedInject` used to give (arg reaches only the store constructor) is gone. These tests
 * pin the guard on the known-NEGATIVE anchor (a Handler injecting the arg must fail) and the
 * known-POSITIVE anchor (the Store's primary constructor must pass), plus the no-op cases the rule must
 * not touch.
 */
internal class ScreenInjectionRuleTest {

    private val rule = ScreenInjectionRule()

    // ---- known-POSITIVE anchors: the legitimate sinks pass ----

    @Test
    fun `Store primary constructor taking the route arg passes`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.image_viewer.mvi.store

            import dev.zacsweers.metro.Inject
            import io.github.stslex.workeeper.core.ui.navigation.Screen

            @Inject
            class ImageViewerStoreImpl internal constructor(
                screen: Screen.ExerciseImage,
                handler: ClickHandler,
            )
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "the Store's primary constructor is where the route arg belongs")
    }

    /**
     * A 0-finding assertion cannot distinguish "the `*StoreImpl` exemption spared this class" from "the
     * rule never visited it" — and the second reading is what a broken rule looks like. This pins the
     * difference: the SAME source, with only the class name changed off the Store suffix, must fail. If
     * this test and the one above ever go green together, the exemption is doing the work; if both go
     * green, the rule has stopped visiting and the guarantee is gone.
     */
    @Test
    fun `the Store exemption is what spares the primary constructor, not a skipped visit`() {
        fun lintNamed(className: String) = rule.lint(
            """
            package io.github.stslex.workeeper.feature.image_viewer.mvi.store

            import dev.zacsweers.metro.Inject
            import io.github.stslex.workeeper.core.ui.navigation.Screen

            @Inject
            class $className internal constructor(
                screen: Screen.ExerciseImage,
            )
            """.trimIndent(),
        )

        assertEquals(0, lintNamed("ImageViewerStoreImpl").size, "the Store suffix must exempt")
        assertEquals(
            1,
            lintNamed("ImageViewerStore").size,
            "the identical class off the Store suffix must fail — otherwise the green above is a skip",
        )
    }

    /**
     * The six not-yet-ported features still carry the pre-arc shape (`@AssistedInject` on the constructor,
     * `@Assisted` on the route arg). They must keep passing while the arc is in flight. The exemption
     * test above pins that this green is the Store exemption rather than a skipped visit.
     */
    @Test
    fun `assisted Store primary constructor keeps passing during the arc`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.single_training.mvi.store

            import dev.zacsweers.metro.Assisted
            import dev.zacsweers.metro.AssistedInject
            import io.github.stslex.workeeper.core.ui.navigation.Screen

            class SingleTrainingStoreImpl @AssistedInject constructor(
                @Assisted screen: Screen.Training,
                navigationHandler: NavigationHandler,
            )
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "the un-ported assisted Store shape must not regress mid-arc")
    }

    @Test
    fun `graph extension factory declaring the arg is not a class injection and passes`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.image_viewer.di

            import dev.zacsweers.metro.GraphExtension
            import dev.zacsweers.metro.Provides
            import io.github.stslex.workeeper.core.ui.navigation.Screen

            @GraphExtension(ImageViewerScope::class)
            interface ImageViewerGraph {

                @GraphExtension.Factory
                fun interface Factory {
                    fun createImageViewerGraph(@Provides screen: Screen.ExerciseImage): ImageViewerGraph
                }
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "the factory creator is the legitimate entry point for the arg")
    }

    @Test
    fun `non-injected class taking a Screen passes`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.image_viewer.mvi.mapper

            import io.github.stslex.workeeper.core.ui.navigation.Screen

            class ScreenTitleMapper(private val screen: Screen.ExerciseImage)
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "a class that is not DI-constructed cannot resolve the arg from a graph")
    }

    @Test
    fun `injected class with an unrelated type whose name starts with Screen passes`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.image_viewer.mvi.handler

            import dev.zacsweers.metro.Inject

            @Inject
            class ClickHandler(private val size: ScreenSize)
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "ScreenSize is not the navigation Screen type")
    }

    // ---- known-NEGATIVE anchors: resolving the arg from the graph fails ----

    @Test
    fun `Handler injecting the route arg fails`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.image_viewer.mvi.handler

            import dev.zacsweers.metro.Inject
            import io.github.stslex.workeeper.core.ui.navigation.Screen

            @Inject
            class ClickHandler(private val screen: Screen.ExerciseImage)
            """.trimIndent(),
        )

        assertEquals(1, findings.size, "a Handler must not resolve the route arg from the feature graph")
        assertTrue(
            findings.first().message.contains("reading navigation state out of DI"),
            "the finding must explain the bypass it prevents: ${findings.first().message}",
        )
    }

    @Test
    fun `Interactor injecting the route arg via constructor annotation fails`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.image_viewer.domain

            import dev.zacsweers.metro.Inject
            import io.github.stslex.workeeper.core.ui.navigation.Screen

            class ImageViewerInteractorImpl @Inject constructor(
                private val screen: Screen,
            )
            """.trimIndent(),
        )

        assertEquals(1, findings.size, "the bare Screen supertype is caught too, and @Inject on the ctor counts")
    }

    @Test
    fun `fully qualified route arg type is caught`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.image_viewer.mvi.handler

            import dev.zacsweers.metro.Inject

            @Inject
            class NavigationHandler(
                private val screen: io.github.stslex.workeeper.core.ui.navigation.Screen.Training,
            )
            """.trimIndent(),
        )

        assertEquals(1, findings.size, "a fully-qualified route arg must not dodge the rule")
    }

    @Test
    fun `AssistedInject class outside a Store also fails`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.image_viewer.mvi.handler

            import dev.zacsweers.metro.Assisted
            import dev.zacsweers.metro.AssistedInject
            import io.github.stslex.workeeper.core.ui.navigation.Screen

            @AssistedInject
            class PagingHandler(@Assisted private val screen: Screen.ExerciseImage)
            """.trimIndent(),
        )

        assertEquals(1, findings.size, "assisted injection outside a Store is still a bypass")
    }

    @Test
    fun `Store taking the route arg in a SECONDARY constructor fails`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.image_viewer.mvi.store

            import dev.zacsweers.metro.Inject
            import io.github.stslex.workeeper.core.ui.navigation.Screen

            @Inject
            class ImageViewerStoreImpl internal constructor(handler: ClickHandler) {

                constructor(screen: Screen.ExerciseImage) : this(ClickHandler())
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size, "the arg enters only through the PRIMARY constructor, even on a Store")
    }
}
