// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.asContribution
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
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
 * The settings extension inherits the parent graph's app-scoped bindings by identity: the qualified
 * dispatcher pair distinctly, and the bare `Context` as the parent factory's own bound instance.
 */
internal class SettingsExtensionIdentityTest {

    // The parent graph binds Dispatchers.Main.immediate, so a JVM test must install a Main first.
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
            appScopeLifetime = AppScopeLifetime(),
            databaseReplacement = mockk(relaxed = true),
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
     * The Store cannot construct off-device: the inherited auth stack static-inits Play Services.
     * The boundary is asserted instead — construction must reach the real inherited auth provider.
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
        // Both assertSame calls above still pass if the parent holds one instance for both keys.
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
