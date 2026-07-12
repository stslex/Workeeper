// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

import android.content.Context
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import java.io.File

/**
 * App-Scope Collapse Step 3 (SB1). Hilt `@Inject`/`@Singleton` stripped, Hilt `@Binds` removed from
 * PlatformModule; now Metro-owned via `@ContributesBinding(AppScope)` (auto-aggregated by the app-scope
 * AppGraph). `@SingleIn(AppScope)` = process-lifetime single-owner. `public` for cross-module aggregation
 * (D1 — the merged AppGraph in :app:app cannot extend an internal contribution; never hand-construct,
 * resolve via DI). Context is PLAIN (Metro resolves it from the graph's create(applicationContext); the
 * Hilt `@ApplicationContext` qualifier is not javax so includeJavax does not carry it).
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidTempFileProvider(
    private val context: Context,
) : TempFileProvider {

    override fun createTempFile(prefix: String, suffix: String): File =
        File.createTempFile(prefix, suffix, context.cacheDir)
}
