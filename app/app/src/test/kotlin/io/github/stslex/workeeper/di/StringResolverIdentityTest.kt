// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.ui.kit.strings.ComposeStringResolver
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * kmp-string-resolver.md §5.3: the app graph binds `StringResolver` to [ComposeStringResolver]
 * and hands out one app-scoped instance.
 */
internal class StringResolverIdentityTest {

    private fun buildAppGraph(): AppGraph = createGraphFactory<AppGraph.Factory>()
        .create(
            applicationContext = mockk<Context>(relaxed = true),
            appDatabase = mockk(relaxed = true),
            imageStorage = mockk(relaxed = true),
            appScopeLifetime = AppScopeLifetime(),
            databaseReplacement = mockk(relaxed = true),
        )

    @Test
    fun `app graph binds the Compose string resolver`() {
        val appGraph = buildAppGraph()

        assertInstanceOf(ComposeStringResolver::class.java, appGraph.stringResolver)
    }

    @Test
    fun `two reads give the one app-scoped resolver`() {
        val appGraph = buildAppGraph()

        assertSame(
            appGraph.stringResolver,
            appGraph.stringResolver,
            "StringResolver must be app-scoped: one instance per graph",
        )
    }
}
