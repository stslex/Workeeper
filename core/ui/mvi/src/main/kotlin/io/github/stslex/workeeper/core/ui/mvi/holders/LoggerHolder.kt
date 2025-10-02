package io.github.stslex.workeeper.core.ui.mvi.holders

import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.mvi.BaseStore.Companion.STORE_LOGGER_PREFIX
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoggerHolder @Inject constructor() {

    fun create(name: String): Logger = Log.tag("${STORE_LOGGER_PREFIX}_$name")
}
