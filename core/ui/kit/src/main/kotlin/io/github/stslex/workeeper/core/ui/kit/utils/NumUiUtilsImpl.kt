package io.github.stslex.workeeper.core.ui.kit.utils

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.utils.NumUiUtils as NumUiUtilsCore

/**
 * Metro-owned via `@ContributesBinding(AppScope)`, which the app-scope `AppGraph`
 * (`@DependencyGraph(AppScope::class)`) auto-aggregates. `@SingleIn(AppScope)` gives a process-lifetime
 * single owner. Contribution (not an app/app `@Provides`) is used because this impl is `internal`-tier to
 * `core:ui:kit` — the AppGraph in `app/app` cannot reference impl types directly, so ownership lives at the
 * impl via the visibility-respecting Metro mechanic.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class NumUiUtilsImpl : NumUiUtils {

    override fun roundThousand(value: Double): Double = NumUiUtilsCore.roundThousand(value)
}
