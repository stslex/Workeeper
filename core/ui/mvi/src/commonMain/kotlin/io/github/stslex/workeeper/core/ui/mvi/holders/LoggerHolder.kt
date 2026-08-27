package io.github.stslex.workeeper.core.ui.mvi.holders

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.mvi.BaseStore.Companion.STORE_LOGGER_PREFIX

/** App-scoped `@Inject` holder; the graph exposes it through a `val loggerHolder` accessor. */
@SingleIn(AppScope::class)
@Inject
class LoggerHolder {

    fun create(name: String): Logger = Log.tag("${STORE_LOGGER_PREFIX}_$name")
}
