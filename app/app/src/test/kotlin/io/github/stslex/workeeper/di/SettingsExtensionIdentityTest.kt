// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.asContribution
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.feature.settings.di.SettingsGraph
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Replaces the former feature-module `SettingsGraphBridgeTest` (a `@GraphExtension` cannot be created
 * standalone, so the assertion must run where the parent [AppGraph] is compiled — here, `:app`).
 *
 * settings is the arc's widest graph: 18 formerly hand-threaded `@Provides` bound instances, now all
 * inherited. The two properties the old bridge test pinned did not go away when the factory params did —
 * they got HARDER to verify, because inheritance across a graph boundary is implicit where a
 * `create(...)` argument list was explicit. Both are re-pinned here, against the real parent graph:
 *
 *  1. **Qualified-dispatcher pair.** settings reads `@DefaultDispatcher` AND `@IODispatcher` — two
 *     bindings of the SAME type separated only by qualifier. Each must inherit as the parent's own
 *     instance, and the two must stay distinct. A silent cross-wire (both resolving to one dispatcher)
 *     would still compile and would be invisible from the Store alone, which is why the graph exposes
 *     them as inert accessors.
 *  2. **Bare, unqualified `Context`.** Formerly a per-graph `create(context = ...)` argument; now
 *     inherited from the parent's `create(applicationContext = ...)` bound instance. Asserted against
 *     the very instance handed to the parent factory — identity end to end, not merely non-null.
 *
 * Plus aggregation into the real parent, shared with [ArchiveExtensionIdentityTest] and
 * [AllTrainingsExtensionIdentityTest]. Those siblings also assert `store.analyticsHolder === parent's`;
 * settings proves the same inheritance through the graph accessors above instead, because its Store can
 * no longer be constructed off-device — see the Play-Services test below for why that is a consequence
 * of the port rather than a gap in it.
 */
internal class SettingsExtensionIdentityTest {

    // The real parent graph provides Dispatchers.Main.immediate (DispatchersBindingContainer); a plain
    // JVM test must install a Main dispatcher before the store constructs.
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val appContextMock = mockk<Context>(relaxed = true)

    private fun buildAppGraph(): AppGraph = createGraphFactory<AppGraph.Factory>()
        .create(
            applicationContext = appContextMock,
            appDatabase = mockk(relaxed = true),
            imageStorage = mockk(relaxed = true),
        )

    private fun AppGraph.settingsExtension(): SettingsGraph = asContribution<SettingsGraph.Factory>()
        .createSettingsGraph()

    @Test
    fun `extension aggregates into the real parent graph`() {
        val extension = buildAppGraph().settingsExtension()

        assertNotNull(
            extension,
            "AppGraph must implement the contributed SettingsGraph.Factory and create the extension",
        )
    }

    /**
     * The sibling identity tests assert `!= null` on the Store. settings CANNOT, and the reason is the
     * port itself: the old `SettingsGraphBridgeTest` handed the graph 18 **mocks**, so the Store built
     * fine on plain JVM. The extension inherits the **real** app-scoped stack instead, so constructing
     * the Store now reaches the real `DriveBackupAuth` → `Identity.getAuthorizationClient(context)`, and
     * Google Play Services cannot static-init off-device. That is an environment limit, not a wiring gap:
     * Metro validates bindings at COMPILE time, so a missing binding would have failed the build, never
     * this test.
     *
     * So this asserts the boundary rather than pretending it away — and in doing so makes a STRONGER
     * inheritance claim than `!= null` would: construction must reach the genuine
     * `AuthProvidersBindingContainer` provider inherited from the parent. If settings ever stops pulling
     * the real auth stack (a mock creeping back into app scope, the backup slice being re-threaded), the
     * failure mode changes and this test says so.
     */
    @Test
    fun `store construction reaches the real inherited auth stack and stops only at Play Services`() {
        val extension = buildAppGraph().settingsExtension()

        val thrown = runCatching { extension.settingsStore }.exceptionOrNull()

        assertNotNull(thrown, "Store construction is expected to fail off-device at Play Services")
        val trace = generateSequence(thrown) { it.cause }
            .joinToString("\n") { "${it}\n${it.stackTrace.joinToString("\n")}" }
        assertTrue(
            trace.contains("com.google.android.gms"),
            "Expected a Play-Services init failure; got a different failure:\n$trace",
        )
        assertTrue(
            trace.contains("AuthProvidersBindingContainer"),
            "Construction must go through the REAL auth provider inherited from the parent graph, " +
                "proving the extension resolved app-scoped bindings rather than test doubles:\n$trace",
        )
    }

    @Test
    fun `both qualified dispatchers inherit distinctly across the extension boundary`() {
        val appGraph = buildAppGraph()

        val extension = appGraph.settingsExtension()

        assertSame(
            appGraph.defaultDispatcher,
            extension.defaultDispatcher,
            "@DefaultDispatcher must inherit as the parent's instance, not a fresh one",
        )
        assertSame(
            appGraph.ioDispatcher,
            extension.ioDispatcher,
            "@IODispatcher must inherit as the parent's instance, not a fresh one",
        )
        // The cross-wire this guards: two same-typed bindings separated only by qualifier, collapsing
        // into one across the graph boundary. Both assertSame calls above would still pass if the
        // parent itself held one instance for both keys, so the distinctness claim is made explicitly.
        assertNotSame(
            extension.defaultDispatcher,
            extension.ioDispatcher,
            "@DefaultDispatcher and @IODispatcher must remain two distinct binding keys in the extension",
        )
    }

    @Test
    fun `the bare app Context is inherited from the parent's bound instance`() {
        val extension = buildAppGraph().settingsExtension()

        assertSame(
            appContextMock,
            extension.appContext,
            "The unqualified Context must reach the extension as the parent factory's bound instance",
        )
    }
}
