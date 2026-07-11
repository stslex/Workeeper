package io.github.stslex.workeeper.core.ui.mvi.holders

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.mvi.BaseStore.Companion.STORE_LOGGER_PREFIX

/**
 * App-Scope Collapse Step 3 (SB1). Hilt's `@Inject`/`@Singleton` were stripped; this app-scoped holder
 * is now Metro-owned. `@SingleIn(AppScope)` gives the process-lifetime single-owner the `@Singleton`
 * gave; the class-level `@Inject` makes it constructor-injectable by the graph.
 *
 * NOT `@ContributesBinding`: that binds an impl to a SUPERTYPE, and `LoggerHolder` is a concrete class
 * with no interface. Instead the app-scope `AppGraph` exposes a `val loggerHolder: LoggerHolder`
 * accessor, which pulls this scoped `@Inject` class into the graph as a self-bound singleton. The 13
 * `*HiltEntryPoint.loggerHolder()` accessors + the `BaseStore` ctor param resolve it through the single
 * adopt-back `@Provides` in `AppGraphAdoptBackModule`, which delegates to that accessor (single-owner).
 */
@SingleIn(AppScope::class)
@Inject
class LoggerHolder {

    fun create(name: String): Logger = Log.tag("${STORE_LOGGER_PREFIX}_$name")
}
