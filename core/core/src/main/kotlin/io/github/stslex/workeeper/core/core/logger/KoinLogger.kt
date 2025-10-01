package io.github.stslex.workeeper.core.core.logger

import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import org.koin.core.logger.MESSAGE

class KoinLogger(isDebug: Boolean) : Logger(
    if (isDebug) {
        Level.DEBUG
    } else {
        Level.WARNING
    },
) {

    private val logger = Log.tag("Koin")

    override fun display(level: Level, msg: MESSAGE) {
        when (level) {
            Level.DEBUG -> logger.d(msg)
            Level.INFO -> logger.i(msg)
            Level.ERROR -> logger.e(KoinError(msg))
            Level.WARNING -> logger.w(msg)
            Level.NONE -> Unit
        }
    }
}
